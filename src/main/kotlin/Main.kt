import com.clickhouse.jdbc.ClickHouseDataSource
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.telegramBotWithBehaviourAndLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onText
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import org.ocpsoft.prettytime.PrettyTime
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

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

fun toCountry(cc: String) = Locale.of("", cc).displayCountry


suspend fun main() {
    telegramBotWithBehaviourAndLongPolling(System.getenv("BOT_TOKEN"), CoroutineScope(Dispatchers.IO)) {
        onText {
            val text = it.text;

            val session = sessionOf(dataSource)

            if (text == "/stat") {
                val i = AtomicInteger(1)

                val toMember: (Row) -> Pair<String, Int> = { row ->
                    Pair(row.string("country"), row.int("count_of_days"))
                }

                val stat = session.run(
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
                    ).map(toMember).asList
                ).map { "${i.getAndIncrement()} - ${it.first} - ${it.second} (${prettyDays(it.second)})" }
                    .joinToString("\n")

                println(stat + "\n")
                reply(it, stat)

                return@onText
            }

            val arguments = text!!.split(" ")

            val latitude = arguments[0].toFloat().round(5)
            val longitude = arguments[1].toFloat().round(5)
            val ts = toTimeZone(arguments[2])
            val country = toCountry(arguments[3])

            println("lat: ${latitude}, lon: ${longitude}, ts: ${ts}, cc: ${country}\n")

            session.execute(
                queryOf(
                    """
                              INSERT INTO country_days_tracker
                              ( date_time,
                                user_id,
                                latitude,
                                longitude,
                                country,
                                tzname,
                                locality
                              )
                              VALUES (
                                :date_time,
                                :user_id,
                                :latitude,
                                :longitude,
                                :country,
                                :tzname,
                                :locality)
            """,
                    mapOf(
                        "date_time" to ZonedDateTime.now().withZoneSameInstant(ts).toLocalDateTime(),
                        "user_id" to it.chat.id.chatId,
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "country" to country,
                        "tzname" to arguments[2],
                        "locality" to arguments[2]
                    )
                )
            )
            reply(it, country)
        }
    }.second.join()
}
