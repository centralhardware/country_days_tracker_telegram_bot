
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.micro_utils.common.Warning
import dev.inmo.tgbotapi.AppConfig
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.sendLocation
import dev.inmo.tgbotapi.extensions.api.edit.location.live.editLiveLocation
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.longPolling
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.utils.RiskFeature
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import java.time.Duration
import java.time.Instant

private val dbService = DatabaseService()
private val webService = WebService(dbService)
private val activeSubscriptions = ConcurrentHashMap<IdChatIdentifier, Pair<MessageId, Instant>>()

@OptIn(Warning::class, RiskFeature::class)
suspend fun main() {
    AppConfig.init("CountryDaysTrackerBot")
    webService.start(80)

    longPolling({ restrictAccess(EnvironmentVariableUserAccessChecker()) }) {

        launch {
            webService.locationUpdates.collect { update ->
                val now = Instant.now()
                activeSubscriptions.entries.removeIf { (_, pair) ->
                    val (_, startTime) = pair
                    val duration = Duration.between(startTime, now)
                    duration.toMinutes() >= 30
                }

                activeSubscriptions.forEach { (chatId, pair) ->
                    val (messageId, _) = pair
                    try {
                        editLiveLocation(
                            chatId = chatId,
                            messageId = messageId,
                            latitude = update.latitude.toDouble(),
                            longitude = update.longitude.toDouble()
                        )
                        KSLog.info("Updated location for chat $chatId to ${update.latitude}, ${update.longitude}")
                    } catch (e: Exception) {
                        KSLog.info("Failed to update location for chat $chatId: ${e.message}")
                    }
                }
            }
        }

        setMyCommands(
                BotCommand("stat", "show statistics"),
                BotCommand("citystat", "show city statistics"),
                BotCommand("subscribe", "subscribe to location updates")
            )
            onCommand("stat") {
                val i = AtomicInteger(1)

                val stat = dbService.getCountryStats()
                val msg = buildString {
                    append(
                        stat.joinToString("\n") {
                            val pretty = prettyTime(it.second)
                            val period = if (pretty.isEmpty()) "" else "($pretty)"
                            "${i.getAndIncrement()} - ${it.first} - ${it.second}$period"
                        }
                    )
                    append("\n\n")
                    append(calculateVisitedPercentage(stat.size) + "\n")
                    val currentCountry = dbService.getCurrentCountryLength()
                    append("Current country: ${currentCountry.first} ${currentCountry.second}")
                }

                KSLog.info(stat)
                reply(it, msg)
            }
            onCommand("citystat") {
                val i = AtomicInteger(1)

                val stat = dbService.getCityStats()
                val msg = stat.joinToString("\n") {
                    val pretty = prettyTime(it.second)
                    val period = if (pretty.isEmpty()) "" else "($pretty)"
                    "${i.getAndIncrement()} - ${it.first} - ${it.second}$period"
                }

                KSLog.info(stat)
                reply(it, msg)
            }

            onCommand("subscribe") {
                val chatId = it.chat.id

                val lastLocation = dbService.getLastLocation()
                val lat = lastLocation?.first?.toDouble() ?: 0.0
                val lon = lastLocation?.second?.toDouble() ?: 0.0

                val message = sendLocation(
                    chatId = chatId,
                    latitude = lat,
                    longitude = lon,
                    livePeriod = 1800
                )

                activeSubscriptions[chatId] = Pair(message.messageId, Instant.now())

                reply(it, "📍 Subscribed! Live location will update automatically when new coordinates arrive.")
            }
        }
        .second
        .join()
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

    val weeks = (remainingDaysAfterMonths / 7)
    val days = (remainingDaysAfterMonths % 7)

    val parts = mutableListOf<String>()

    if (years > 0) parts.add("$years years")
    if (months > 0) parts.add("$months months")
    if (weeks > 0) parts.add("$weeks weeks")
    if (weeks > 0 || months > 0 || years > 0) {
        if (days > 0) parts.add("$days days")
    }

    return parts.joinToString(", ").ifEmpty { "" }
}
