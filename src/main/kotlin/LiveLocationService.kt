import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.types.UserId
import dev.inmo.tgbotapi.types.MessageId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class LiveLocationService {

    data class UserLiveLocationSession(
        val userId: UserId,
        var messageId: MessageId? = null,
        var isActive: Boolean = true
    )

    private val subscribers = ConcurrentHashMap<UserId, UserLiveLocationSession>()
    private val mutex = Mutex()

    var updateLiveLocationCallback: (suspend (UserId, MessageId, Float, Float, String) -> Unit)? = null

    suspend fun subscribeToLocationUpdates(userId: UserId, messageId: MessageId) {
        mutex.withLock {
            subscribers[userId] = UserLiveLocationSession(userId, messageId)
            KSLog.info("User $userId subscribed to location updates with message ID $messageId")
        }
    }

    suspend fun unsubscribeFromLocationUpdates(userId: UserId) {
        mutex.withLock {
            subscribers.remove(userId)
            KSLog.info("User $userId unsubscribed from location updates")
        }
    }

    fun isSubscribed(userId: UserId): Boolean {
        return subscribers.containsKey(userId)
    }

    suspend fun notifyLocationUpdate(latitude: Float, longitude: Float, country: String, locality: String) {
        val updateCallback = updateLiveLocationCallback ?: return

        mutex.withLock {
            subscribers.values.forEach { session ->
                try {
                    if (session.messageId != null) {
                        // Обновляем существующую live location карту
                        updateCallback(session.userId, session.messageId!!, latitude, longitude, "")
                        KSLog.info("Updated live location for user ${session.userId}: $country, $locality")
                    }
                } catch (e: Exception) {
                    KSLog.info("Failed to update location for user ${session.userId}: ${e.message}")
                }
            }
        }
    }
}
