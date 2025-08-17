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
import java.time.Instant
import java.util.*


@Serializable
data class OwnTracksLocation(
    val _type: String,
    val lat: Float,
    val lon: Float,
    val tst: Long,
    val alt: Int? = null,
    val batt: Int? = null,
    val acc: Int? = null,
    val vac: Int? = null,
    val conn: String? = null,
    val p: Double? = null,
    val addr: String? = null,
    val bssid: String? = null,
    val ssid: String? = null,
    val bs: Int? = null,
    val tzname: String? = null,
    val cc: String? = null,
    val locality: String? = null,
    val ghash: String? = null
)

class WebService(private val databaseService: DatabaseService) {

    companion object {
        private const val MAX_ACCURACY_THRESHOLD = 255
        private val jsonRelaxed = Json { ignoreUnknownKeys = true }
    }

    fun start(port: Int = 80): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        KSLog.info("Starting web service on port $port")

        return embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json()
            }

            routing {
                post("/owntracks") {
                    handleOwnTracksUpdate(call)
                }
            }
        }.start(wait = false)
    }


    private suspend fun handleOwnTracksUpdate(call: ApplicationCall) {
        val bodyString = call.receiveText()
        KSLog.info("OwnTracks raw body: $bodyString")
        runCatching {
            val body = jsonRelaxed.decodeFromString<OwnTracksLocation>(bodyString)

            KSLog.info("Processing OwnTracks update: $body")

            // Only handle location messages. Ignore others per requirement to "take only location part".
            if (body._type != "location") {
                call.respond(HttpStatusCode.NoContent)
                return@runCatching
            }

            val latitude = body.lat
            val longitude = body.lon
            val timestamp = body.tst
            val acc = body.acc ?: 0

            if (acc > MAX_ACCURACY_THRESHOLD) {
                KSLog.info("OwnTracks update rejected due to low accuracy: ${acc}m (threshold: ${MAX_ACCURACY_THRESHOLD}m)")
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Location accuracy (${acc}m) is below the required threshold (${MAX_ACCURACY_THRESHOLD}m)."
                )
                return
            }

            val tz: TimeZone = (body.tzname?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault())
            val zoneId = tz.toZoneId()

            val countryName = body.cc?.toCountry() ?: ""

            databaseService.save(
                Instant.ofEpochSecond(timestamp).atZone(zoneId).toLocalDateTime(),
                latitude,
                longitude,
                zoneId,
                countryName,
                body.alt ?: 0,
                body.batt ?: 0,
                acc,
                body.vac ?: 0,
                body.conn ?: "",
                body.locality ?: "",
                body.ghash ?: "",
                body.p ?: 0.0,
                body.addr ?: "",
                body.bssid,
                body.ssid,
                body.bs
            )
        }.onSuccess {
            KSLog.info("Successfully saved OwnTracks update")
            // Many OwnTracks clients accept 200 or 204. Use 200 OK.
            call.respond(HttpStatusCode.OK)
        }.onFailure { error ->
            KSLog.info("Failed to process OwnTracks update: ${error.message}. Body: $bodyString")
            call.respond(
                HttpStatusCode.BadRequest,
                "Failed to process OwnTracks data: ${error.message}"
            )
        }
    }

    private fun String.toTimeZone() = TimeZone.getTimeZone(this).toZoneId()

    private fun String.toCountry() = try {
        Locale.of("en", this).displayCountry
    } catch (e: Exception) {
        ""
    }
}