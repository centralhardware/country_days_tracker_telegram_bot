import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.telegramBotWithBehaviourAndLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onText
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.ocpsoft.prettytime.PrettyTime
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors


fun Float.round(scale: Int): Float =
    BigDecimal(this.toDouble()).setScale(scale, RoundingMode.HALF_UP).toFloat()

fun prettyDays(countOfDays: Int): String {
    if (countOfDays < 7) return ""

    val prettyTime = PrettyTime(Locale.US)
    return " (" + prettyTime.formatDuration(LocalDateTime.now().plusDays(countOfDays.toLong())) + ")"
}

fun toTimeZone(ts: String): ZoneId = TimeZone.getTimeZone(ts).toZoneId()

fun toCountry(cc: String) = Locale.of("", cc).displayCountry


suspend fun main() {
    telegramBotWithBehaviourAndLongPolling(System.getenv("BOT_TOKEN"), CoroutineScope(Dispatchers.IO)) {
        onText {
            val text = it.text;

            if (text == "/stat"){
                val i = AtomicInteger(1)
                val stat = CountryDaysTrackerMapper.getStat(it.chat.id.chatId)
                    .stream()
                    .map { "${i.getAndIncrement()} - ${it.first} - ${it.second} ${prettyDays(it.second)}" }
                    .collect(Collectors.joining("\n"))
                reply(it, stat)

                return@onText
            }

            val arguments = text!!.split(" ")

            val latitude = arguments[0].toFloat().round(5)
            val longitude = arguments[1].toFloat().round(5)
            val altitude = arguments[2].replace(",", ".").split("\\.")[0].toInt()
            val ts = toTimeZone(arguments[3])
            val country = toCountry(arguments[4])

            println("lat: ${latitude}, lon: ${longitude}, alt: ${altitude}, ts: ${ts}, cc: ${country}\n")
            CountryDaysTrackerMapper.insert(
                Track(
                    ZonedDateTime.now().withZoneSameInstant(ts).toLocalDateTime(),
                    it.chat.id.chatId,
                    latitude,
                    longitude,
                    altitude,
                    country
                )
            )
            reply(it, country)
        }
    }.second.join()
}
