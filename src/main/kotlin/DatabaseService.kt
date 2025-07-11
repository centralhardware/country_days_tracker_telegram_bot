import com.clickhouse.jdbc.DataSourceImpl
import kotliquery.queryOf
import kotliquery.sessionOf
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Properties
import javax.sql.DataSource

class DatabaseService {
    val dataSource: DataSource = try {
        DataSourceImpl(System.getenv("CLICKHOUSE_URL"), Properties())
    } catch (e: SQLException) {
        throw RuntimeException(e)
    }

    fun save(
        dateTime: LocalDateTime,
        latitude: Float,
        longitude: Float,
        ts: ZoneId,
        country: String,
        alt: Int,
        batt: Int,
        acc: Int,
        vac: Int,
        conn: String,
        locality: String,
        ghash: String,
        p: Double,
        addr: String,
        bssid: String?,
        ssid: String?,
        bs: Int?
    ) {
        sessionOf(dataSource).use { session ->
            session.execute(
                queryOf(
                    // language=SQL
                    """
                        INSERT INTO country_days_tracker_bot.country_days_tracker (
                            date_time,
                            latitude,
                            longitude,
                            country,
                            tzname,
                            alt,
                            batt,
                            acc,
                            vac,
                            conn,
                            locality,
                            ghash,
                            p,
                            addr,
                            bssid,
                            ssid,
                            bs
                        )
                        SELECT
                            toDateTime(?) AS date_time,
                            toFloat32(?) AS latitude,
                            toFloat32(?) AS longitude,
                            toString(?) AS country,
                            toString(?) AS tzname,
                            toUInt16(?) AS alt,
                            toUInt8(?) AS batt,
                            toUInt8(?) AS acc,
                            toUInt8(?) AS vac,
                            toString(?) AS conn,
                            toString(?) AS locality,
                            toString(?) AS ghash,
                            toFloat64(?) AS p,
                            toString(?) AS addr,
                            toString(?) AS bssid,
                            toString(?) AS ssid,
                            toUInt32(?) AS bs
                    """.trimIndent(),
                    dateTime,
                    latitude,
                    longitude,
                    country,
                    ts.id,
                    alt,
                    batt,
                    acc,
                    vac,
                    conn,
                    locality,
                    ghash,
                    p,
                    addr,
                    bssid,
                    ssid,
                    bs
                )

            )
        }
    }


    fun getCountryStats(): List<Pair<String, Int>> {
        return sessionOf(dataSource)
            .run(
                queryOf(
                    // language=SQL
                    """
                        SELECT country, COUNT(*) AS count_of_days
                        FROM (
                            SELECT DISTINCT LOWER(country) AS country, toStartOfDay(date_time)
                            FROM country_days_tracker_bot.country_days_tracker
                        )
                        GROUP BY country
                        ORDER BY COUNT(*) DESC
                    """.trimIndent(),
                    mapOf(),
                )
                    .map { row ->
                        Pair(row.string("country"), row.int("count_of_days"))
                    }
                    .asList
            )
    }

    fun getCurrentCountryLength(): Pair<String, Int> {
        return sessionOf(dataSource)
            .run(
                queryOf(
                    // language=SQL
                    """
                        WITH
                            data AS (
                                SELECT
                                    toDate(date_time) AS day,
                                    country
                                FROM country_days_tracker_bot.country_days_tracker
                                GROUP BY day, country
                            ),
                            with_sessions AS (
                                SELECT
                                    *,
                                    row_number() OVER (ORDER BY day) -
                                    row_number() OVER (PARTITION BY country ORDER BY day) AS session_id
                                FROM data
                            ),
                            sessions_grouped AS (
                                SELECT
                                    country,
                                    MIN(day) AS start_day,
                                    MAX(day) AS end_day,
                                    COUNT() AS days_in_country
                                FROM with_sessions
                                GROUP BY country, session_id
                            )
                        SELECT
                            country,
                            days_in_country
                        FROM (
                            SELECT
                                *,
                                row_number() OVER (ORDER BY end_day DESC) AS rn
                            FROM sessions_grouped
                        )
                        WHERE rn = 1
                    """.trimIndent(),
                    mapOf()
                ).map { row -> Pair(row.string("country"), row.int("days_in_country")) }.asSingle
            )!!
    }
}
