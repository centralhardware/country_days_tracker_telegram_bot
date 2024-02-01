import java.time.LocalDateTime

data class Track(
    val dateTime: LocalDateTime,
    val userId: Long,
    val latitude: Float,
    val longitude: Float,
    val country: String
)
