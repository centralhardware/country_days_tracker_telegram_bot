import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.ZoneId
import java.util.*

@Serializable
data class LocationRequest(
    val latitude: Float,
    val longitude: Float,
    val timezone: String,
    val country: String,
    val alt: Int,
    val batt: Int,
    val acc: Int,
    val vac: Int,
    val conn: String,
    val locality: String,
    val ghash: String,
    val p: Double,
    val addr: String
)

class WebService(private val databaseService: DatabaseService) {

    companion object {
        private const val MAX_ACCURACY_THRESHOLD = 255
    }

    fun start(port: Int = 80): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        KSLog.info("Starting web service on port $port")

        return embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json()
            }

            routing {
                post("/location") {
                    handleLocationUpdate(call)
                }
            }
        }.start(wait = false)
    }

    private suspend fun handleLocationUpdate(call: ApplicationCall) {
        val bodyString = call.receiveText()
        try {
            val body = Json.decodeFromString<LocationRequest>(bodyString)

            KSLog.info("Processing location update: $body")

            // Check for accuracy
            if (body.acc > MAX_ACCURACY_THRESHOLD) {
                KSLog.info("Location update rejected due to low accuracy: ${body.acc}m (threshold: ${MAX_ACCURACY_THRESHOLD}m)")
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Location accuracy (${body.acc}m) is below the required threshold (${MAX_ACCURACY_THRESHOLD}m)."
                )
                return
            }

            runCatching {
                databaseService.save(
                    body.latitude,
                    body.longitude,
                    toTimeZone(body.timezone),
                    toCountry(body.country),
                    body.alt,
                    body.batt,
                    body.acc,
                    body.vac,
                    body.conn,
                    body.locality,
                    body.ghash,
                    body.p,
                    body.addr
                )
            }.onSuccess {
                KSLog.info("Successfully saved location update")
                call.respond(HttpStatusCode.OK)
            }.onFailure { error ->
                KSLog.info("Failed to save location update: ${error.message}. Body: $bodyString")
                call.respond(
                    HttpStatusCode.InternalServerError,
                    "Failed to save location data: ${error.message}"
                )
            }
        } catch (e: Exception) {
            KSLog.info("Error processing location update: ${e.message}. Body: $bodyString")
            call.respond(
                HttpStatusCode.InternalServerError,
                "An unexpected error occurred: ${e.message}"
            )
        }
    }

    private fun toTimeZone(ts: String): ZoneId = TimeZone.getTimeZone(ts).toZoneId()

    private fun toCountry(cc: String): String = Locale.of("en", cc).displayCountry
}