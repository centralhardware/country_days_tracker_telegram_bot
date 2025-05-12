
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.micro_utils.common.Warning
import dev.inmo.tgbotapi.AppConfig
import dev.inmo.tgbotapi.Trace
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.from
import dev.inmo.tgbotapi.longPolling
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.utils.RiskFeature
import me.centralhardware.telegram.EnvironmentVariableUserAccessChecker
import me.centralhardware.telegram.restrictAccess
import java.util.concurrent.atomic.AtomicInteger

// Service instances
private val dbService = DatabaseService()
private val webService = WebService(dbService)

@OptIn(Warning::class, RiskFeature::class)
suspend fun main() {
    AppConfig.init("CountryDaysTrackerBot")
    // Start the web service
    webService.start(80)
    longPolling({ restrictAccess(EnvironmentVariableUserAccessChecker()) }) {
            setMyCommands(
                BotCommand("stat", "show statistics"),
                BotCommand("trips", "show date ranges of trips to a specified country")
            )
            onCommand("stat") {
                val i = AtomicInteger(1)

                Trace.save("checkStat", mapOf("chatId" to it.from!!.id.chatId.long.toString()))
                val stat = dbService.getCountryStats()
                val msg = buildString {
                    append(stat.joinToString("\n") { "${i.getAndIncrement()} - ${it.first} - ${it.second}(${prettyTime(it.second)})" })
                    append("\n\n")
                    append(calculateVisitedPercentage(stat.size) + "\n")
                    val currentCountry = dbService.getCurrentCountryLength()
                    append("Current country: ${currentCountry.first} ${currentCountry.second}")
                }

                KSLog.info(stat)
                reply(it, msg)
            }

            onCommandWithArgs("trips") { it, args ->
                val commandText = it.content.text

                if (args.isNotEmpty()) {
                    val countryName = args.first()

                    // Get trip date ranges for the specified country
                    val trips = dbService.getTrips(countryName)

                    if (trips.isEmpty()) {
                        reply(it, "No trips to country '$countryName' found.")
                    } else {
                        // Find the longest stay
                        val longestStay = trips.maxByOrNull { it.third } ?: trips.first()
                        val longestStayDays = longestStay.third

                        val msg = buildString {
                            append("Trips to ${trips.first().first}:\n\n")
                            append("Longest stay: ${longestStayDays} ${getDaysWord(longestStayDays)}\n\n")
                            trips.forEachIndexed { index, trip ->
                                val (_, dateRange, days) = trip
                                val (startDate, endDate) = dateRange
                                append("${index + 1}. ${startDate} - ${endDate} (${days} ${getDaysWord(days)})\n")
                            }
                        }
                        reply(it, msg)
                    }
                } else {
                    reply(it, "Please use the format: /trips /t <country name>")
                }
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

fun getDaysWord(days: Int): String {
    return if (days == 1) "day" else "days"
}
