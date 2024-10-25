import com.clickhouse.jdbc.ClickHouseDataSource
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.AppConfig
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.longPolling
import dev.inmo.tgbotapi.types.BotCommand
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotliquery.queryOf
import kotliquery.sessionOf
import me.centralhardware.telegram.EnvironmentVariableUserAccessChecker
import me.centralhardware.telegram.restrictAccess
import org.ocpsoft.prettytime.PrettyTime
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

fun prettyDays(countOfDays: Int): String {
    if (countOfDays < 7) return ""

    return PrettyTime(Locale.US).formatDuration(LocalDateTime.now().plusDays(countOfDays.toLong()))
}

fun toTimeZone(ts: String): ZoneId = TimeZone.getTimeZone(ts).toZoneId()

fun toCountry(cc: String): String = Locale.of("en", cc).displayCountry

suspend fun main() {
    AppConfig.init("CountryDaysTrackerBot")
    embeddedServer(Netty, port = 80) {
        routing {
            post("/location") {
                val latitude = call.request.queryParameters["latitude"]?.toFloatOrNull()
                val longitude = call.request.queryParameters["longitude"]?.toFloat()
                val timezone = call.request.queryParameters["timezone"]
                val country = call.request.queryParameters["country"]
                val userId = call.request.queryParameters["userId"]?.toLongOrNull()

                if (latitude == null || longitude == null || timezone == null || country == null || userId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing or invalid query parameters")
                } else {
                    save(latitude, longitude, toTimeZone(timezone), country, userId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }.start(wait = false)
    longPolling({ restrictAccess(EnvironmentVariableUserAccessChecker()) }) {
        setMyCommands(
            BotCommand("stat", "вывести статистику")
        )
        onCommand("stat") {
            val i = AtomicInteger(1)

            val stat = sessionOf(dataSource).run(
                queryOf(
                    """
                                  SELECT country, count(*) as count_of_days
                                  FROM (
                                    SELECT DISTINCT lower(country) as country,toStartOfDay(date_time)
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

            KSLog.info(stat)
            reply(it, stat)
        }
    }.second.join()
}

fun save(latitude: Float, longitude: Float, ts: ZoneId, country: String, userId: Long) {
    KSLog.info("lat: $latitude, lon: $longitude, ts: $ts, cc: $country")

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
                "user_id" to userId,
                "latitude" to latitude,
                "longitude" to longitude,
                "country" to toCountry(country),
                "tzname" to ts
            )
        )
    )
}
