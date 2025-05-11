
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.ZoneId
import java.util.*

/**
 * Service responsible for handling web requests related to location tracking.
 */
class WebService(private val databaseService: DatabaseService) {

    /**
     * Starts the web server on the specified port.
     * 
     * @param port The port to run the server on
     * @return The EmbeddedServer instance
     */
    fun start(port: Int = 80): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        KSLog.info("Starting web service on port $port")

        return embeddedServer(Netty, port = port) {
            routing {
                post("/location") {
                    handleLocationUpdate(call)
                }
            }
        }.start(wait = false)
    }

    /**
     * Handles location update requests.
     * 
     * @param call The ApplicationCall containing the request
     */
    private suspend fun handleLocationUpdate(call: ApplicationCall) {
        try {
            val latitude = call.request.queryParameters["latitude"]?.toFloatOrNull()
            val longitude = call.request.queryParameters["longitude"]?.toFloatOrNull()
            val timezone = call.request.queryParameters["timezone"]
            val country = call.request.queryParameters["country"]
            val userId = call.request.queryParameters["userId"]?.toLongOrNull()

            // Additional parameters
            val alt = call.request.queryParameters["alt"]?.toIntOrNull()
            val batt = call.request.queryParameters["batt"]?.toIntOrNull()
            val acc = call.request.queryParameters["acc"]?.toIntOrNull()
            val vac = call.request.queryParameters["vac"]?.toIntOrNull()
            val conn = call.request.queryParameters["conn"]
            val locality = call.request.queryParameters["locality"]
            val ghash = call.request.queryParameters["ghash"]
            val p = call.request.queryParameters["p"]?.toDoubleOrNull()
            val addr = call.request.queryParameters["addr"]

            if (latitude == null || longitude == null || timezone == null || 
                country == null || userId == null || alt == null || batt == null || 
                acc == null || vac == null || conn == null || locality == null || 
                ghash == null || p == null || addr == null) {

                KSLog.info("Received invalid location update request: missing or invalid parameters")
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Missing or invalid query parameters"
                )
                return
            }

            KSLog.info("Processing location update: lat=$latitude, lon=$longitude, country=$country, userId=$userId, " +
                    "alt=$alt, batt=$batt, acc=$acc, vac=$vac, conn=$conn, locality=$locality, ghash=$ghash, p=$p, addr=$addr")

            runCatching { 
                databaseService.save(
                    latitude, 
                    longitude, 
                    toTimeZone(timezone), 
                    toCountry(country), 
                    userId,
                    alt,
                    batt,
                    acc,
                    vac,
                    conn,
                    locality,
                    ghash,
                    p,
                    addr
                ) 
            }.onSuccess {
                KSLog.info("Successfully saved location update")
                call.respond(HttpStatusCode.OK)
            }.onFailure { error ->
                KSLog.info("Failed to save location update: ${error.message}")
                call.respond(
                    HttpStatusCode.InternalServerError,
                    "Failed to save location data: ${error.message}"
                )
            }
        } catch (e: Exception) {
            KSLog.info("Error processing location update: ${e.message}")
            call.respond(
                HttpStatusCode.InternalServerError,
                "An unexpected error occurred: ${e.message}"
            )
        }
    }

    /**
     * Converts a timezone string to a ZoneId.
     */
    private fun toTimeZone(ts: String): ZoneId = TimeZone.getTimeZone(ts).toZoneId()

    /**
     * Converts a country code to a country name.
     */
    private fun toCountry(cc: String): String = Locale.of("en", cc).displayCountry
}
