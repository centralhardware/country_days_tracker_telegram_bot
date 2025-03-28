
import com.clickhouse.jdbc.ClickHouseDataSource
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.micro_utils.common.Warning
import dev.inmo.tgbotapi.AppConfig
import dev.inmo.tgbotapi.Trace
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.from
import dev.inmo.tgbotapi.longPolling
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.utils.RiskFeature
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotliquery.queryOf
import kotliquery.sessionOf
import me.centralhardware.telegram.EnvironmentVariableUserAccessChecker
import me.centralhardware.telegram.restrictAccess
import java.sql.SQLException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

val dataSource: DataSource =
    try {
        ClickHouseDataSource(System.getenv("CLICKHOUSE_URL"))
    } catch (e: SQLException) {
        throw RuntimeException(e)
    }

fun toTimeZone(ts: String): ZoneId = TimeZone.getTimeZone(ts).toZoneId()

fun toCountry(cc: String): String = Locale.of("en", cc).displayCountry

@OptIn(Warning::class, RiskFeature::class)
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

                    if (
                        latitude == null ||
                            longitude == null ||
                            timezone == null ||
                            country == null ||
                            userId == null
                    ) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing or invalid query parameters",
                        )
                    } else {
                        runCatching { save(latitude, longitude, toTimeZone(timezone), country, userId) }
                            .onFailure(::println)
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
        .start(wait = false)
    longPolling({ restrictAccess(EnvironmentVariableUserAccessChecker()) }) {
            setMyCommands(BotCommand("stat", "вывести статистику"))
            onCommand("stat") {
                val i = AtomicInteger(1)

                Trace.save("checkStat", mapOf("chatId" to it.from!!.id.chatId.long.toString()))
                val stat =
                    sessionOf(dataSource)
                        .run(
                            queryOf(
                                    """
                                  SELECT country, count(*) as count_of_days
                                  FROM (
                                    SELECT DISTINCT lower(country) as country,toStartOfDay(date_time)
                                    FROM country_days_tracker_bot.country_days_tracker
                                    WHERE user_id = :user_id
                                  )
                                  GROUP BY country
                                  ORDER BY count(*) DESC
                """,
                                    mapOf("user_id" to it.chat.id.chatId),
                                )
                                .map { row ->
                                    Pair(row.string("country"), row.int("count_of_days"))
                                }
                                .asList
                        )
                val msg = buildString {
                    append(stat.joinToString("\n") { "${i.getAndIncrement()} - ${it.first} - ${it.second}(${prettyTime(it.second)})" })
                    append("\n\n")
                    append(calculateVisitedPercentage(stat.size) + "\n")
                    append("Current country:" + getCurrentCountryLength())
                }

                KSLog.info(stat)
                reply(it, msg)
            }
        }
        .second
        .join()
}

fun save(latitude: Float, longitude: Float, ts: ZoneId, country: String, userId: Long) {
    KSLog.info("lat: $latitude, lon: $longitude, ts: $ts, cc: $country")

    sessionOf(dataSource)
        .execute(
            queryOf(
                """
                              INSERT INTO country_days_tracker_bot.country_days_tracker
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
                    "tzname" to ts.id,
                ),
            )
        )
}

const val TOTAL_COUNTRIES = 193
fun calculateVisitedPercentage(visitedCountries: Int): String {
    val percent =  (visitedCountries.toDouble() / TOTAL_COUNTRIES) * 100
    return "You have visited %.2f%% of the world".format(percent)
}

fun prettyTime(totalDays: Int): String {
    val years = (totalDays / 365.25).toInt()
    val remainingDaysAfterYears = (totalDays % 365.25).toInt()

    val months = (remainingDaysAfterYears / 30.44).toInt()
    val remainingDaysAfterMonths = (remainingDaysAfterYears % 30.44).toInt()

    val weeks = (remainingDaysAfterMonths / 7).toInt()
    val days = (remainingDaysAfterMonths % 7).toInt()

    val parts = mutableListOf<String>()

    if (years > 0) parts.add("$years years")
    if (months > 0) parts.add("$months months")
    if (weeks > 0) parts.add("$weeks weeks")
    if (weeks > 0 || months > 0 || years > 0) {
        if (days > 0) parts.add("$days days")
    }

    return parts.joinToString(", ").ifEmpty { "" }
}

fun getCurrentCountryLength(): String {
    var res = sessionOf(dataSource)
        .run(queryOf(
            """
               WITH
    -- Убираем время, оставляем только дату
    data AS (
        SELECT
            user_id,
            toDate(date_time) AS day,
            country
        FROM country_days_tracker
        GROUP BY
            user_id, day, country
    ),

    -- Определяем границы "сессий" по смене страны
    with_sessions AS (
        SELECT
            *,
            row_number() OVER (PARTITION BY user_id ORDER BY day) -
            row_number() OVER (PARTITION BY user_id, country ORDER BY day) AS session_id
        FROM data
    ),

    -- Группируем по сессиям, считаем продолжительность
    sessions_grouped AS (
        SELECT
            user_id,
            country,
            min(day) AS start_day,
            max(day) AS end_day,
            count() AS days_in_country
        FROM with_sessions
        GROUP BY
            user_id, country, session_id
    )

-- Выводим только текущую сессию (максимальную по дате)
SELECT
    country,
    days_in_country
FROM (
         SELECT *,
                row_number() OVER (PARTITION BY user_id ORDER BY end_day DESC) AS rn
         FROM sessions_grouped
         )
WHERE rn = 1 
            """, mapOf()
        ).map { row -> Pair(row.string("country"), row.int("days_in_country")) }.asSingle)!!
    return "${res.first} ${res.second}"
}