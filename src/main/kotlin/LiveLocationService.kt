import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.types.UserId
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class LiveLocationService {

    data class UserLiveLocationSession(
        val chatId: IdChatIdentifier,
        var messageId: MessageId? = null,
        var isActive: Boolean = true
    )

    private val subscribers = ConcurrentHashMap<IdChatIdentifier, UserLiveLocationSession>()
    private val mutex = Mutex()

    var updateLiveLocationCallback: (suspend (IdChatIdentifier, MessageId, Float, Float, String) -> Unit)? = null

    suspend fun subscribeToLocationUpdates(chatId: IdChatIdentifier, messageId: MessageId) {
        mutex.withLock {
            subscribers[chatId] = UserLiveLocationSession(chatId, messageId)
            KSLog.info("Chat $chatId subscribed to location updates with message ID $messageId")
        }
    }

    suspend fun unsubscribeFromLocationUpdates(chatId: IdChatIdentifier) {
        mutex.withLock {
            subscribers.remove(chatId)
            KSLog.info("Chat $chatId unsubscribed from location updates")
        }
    }

    fun isSubscribed(chatId: IdChatIdentifier): Boolean {
        return subscribers.containsKey(chatId)
    }

    suspend fun notifyLocationUpdate(latitude: Float, longitude: Float, country: String, locality: String) {
        val updateCallback = updateLiveLocationCallback ?: return

        mutex.withLock {
            subscribers.values.forEach { session ->
                try {
                    if (session.messageId != null) {
                        // Обновляем существующую live location карту
                        updateCallback(session.chatId, session.messageId!!, latitude, longitude, "")
                        KSLog.info("Updated live location for chat ${session.chatId}: $country, $locality")
                    }
                } catch (e: Exception) {
                    KSLog.info("Failed to update location for chat ${session.chatId}: ${e.message}")
                }
            }
        }
    }
}
