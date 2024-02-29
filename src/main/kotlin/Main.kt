import com.clickhouse.jdbc.ClickHouseDataSource
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.telegramBotWithBehaviourAndLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onText
import dev.inmo.tgbotapi.extensions.utils.asCommonUser
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.from
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.text
import dev.inmo.tgbotapi.types.BotCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotliquery.queryOf
import kotliquery.sessionOf
import me.centralhardware.telegram.bot.common.ClickhouseKt
import org.ocpsoft.prettytime.PrettyTime
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

val log = LoggerFactory.getLogger("root")

val dataSource: DataSource = try {
    ClickHouseDataSource(System.getenv("CLICKHOUSE_URL"))
} catch (e: SQLException) {
    throw RuntimeException(e)
}

fun Float.round(scale: Int): Float =
    BigDecimal(this.toDouble()).setScale(scale, RoundingMode.HALF_UP).toFloat()

fun prettyDays(countOfDays: Int): String {
    if (countOfDays < 7) return ""

    return PrettyTime(Locale.US).formatDuration(LocalDateTime.now().plusDays(countOfDays.toLong()))
}

fun toTimeZone(ts: String): ZoneId = TimeZone.getTimeZone(ts).toZoneId()

fun toCountry(cc: String): String = Locale.of("", cc).displayCountry


suspend fun main() {
    val clickhouse = ClickhouseKt()
    telegramBotWithBehaviourAndLongPolling(System.getenv("BOT_TOKEN"),
        CoroutineScope(Dispatchers.IO),
        defaultExceptionsHandler = { t -> log.warn("", t) }) {
        setMyCommands(
            BotCommand("stat", "вывести статистику")
        )
        onCommand("stat") {
            async { clickhouse.log(it.text!!, false, it.from!!.asCommonUser(), "countryDaysTrackerBot") }

            val i = AtomicInteger(1)

            val stat = sessionOf(dataSource).run(
                queryOf(
                    """
                                  SELECT country, count(*) as count_of_days
                                  FROM (
                                    SELECT DISTINCT country,toStartOfDay(date_time)
                                    FROM country_days_tracker
                                    WHERE user_id = :user_id
                                  )
                                  GROUP BY country
                                  ORDER BY count(*) DESC
                """,
                    mapOf("user_id" to it.chat.id.chatId)
                ).map { row ->
                    Pair(row.string("country"), row.int("count_of_days"))
                }.asList
            ).joinToString("\n") { "${i.getAndIncrement()} - ${it.first} - ${it.second} (${prettyDays(it.second)})" }

            log.info(stat)
            reply(it, stat)
        }
        onText {
            val text = it.text
            async { clickhouse.log(it.text!!, false, it.from!!.asCommonUser(), "countryDaysTrackerBot") }

            val arguments = text!!.split(" ")

            val latitude = arguments[0].toFloat().round(5)
            val longitude = arguments[1].toFloat().round(5)
            val ts = toTimeZone(arguments[2])
            val country = toCountry(arguments[3])

            log.info("lat: $latitude, lon: $longitude, ts: $ts, cc: $country")

            sessionOf(dataSource).execute(
                queryOf(
                    """
                              INSERT INTO country_days_tracker
                              ( date_time,
                                user_id,
                                latitude,
                                longitude,
                                country,
                                tzname
                              )
                              VALUES (
                                :date_time,
                                :user_id,
                                :latitude,
                                :longitude,
                                :country,
                                :tzname)
            """,
                    mapOf(
                        "date_time" to ZonedDateTime.now().withZoneSameInstant(ts).toLocalDateTime(),
                        "user_id" to it.chat.id.chatId,
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "country" to country,
                        "tzname" to arguments[2]
                    )
                )
            )
            reply(it, country)
        }
    }.second.join()
}
