
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotliquery.queryOf
import kotliquery.sessionOf
import me.centralhardware.telegram.EnvironmentVariableUserAccessChecker
import me.centralhardware.telegram.restrictAccess
import java.net.URL
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
                val msg = """
                    ${stat.joinToString("\n") { "${i.getAndIncrement()} - ${it.first} - ${it.second}" }}
                    ${calculateVisitedPercentage(stat.size)}    
                    ${calculateVisitedByRegion(stat.map { it.first }.toSet())}
                }
                """.trimIndent()

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

@Serializable
data class CountryInfo(val name: String, val region: String)

fun fetchCountryData(): Map<String, String> {
    val apiUrl = "https://restcountries.com/v3.1/all"
    val response = URL(apiUrl).readText()
    val countries = Json.decodeFromString<List<CountryInfo>>(response)
    return countries.associate { it.name to it.region }
}

val TOTAL_COUNTRIES = 195
fun calculateVisitedPercentage(visitedCountries: Int): String {
    val percent =  (visitedCountries.toDouble() / TOTAL_COUNTRIES) * 100
    return "$percent of world visited"
}

fun calculateVisitedByRegion(visitedCountries: Set<String>): String {
    val countryToRegion = fetchCountryData()
    val regionCounts = countryToRegion.values.groupingBy { it }.eachCount()
    val visitedByRegion = visitedCountries.groupingBy { countryToRegion[it] ?: "Unknown" }.eachCount()

    val stat = visitedByRegion.mapValues { (region, visitedCount) ->
        val total = regionCounts[region] ?: return@mapValues 0.0
        (visitedCount.toDouble() / total) * 100
    }

    return buildString {
        stat.forEach { k,v ->
            append("$k $v\n")
        }
    }
}

