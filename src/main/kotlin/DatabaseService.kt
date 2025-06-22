import com.clickhouse.jdbc.ClickHouseDataSource
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import kotliquery.queryOf
import kotliquery.sessionOf
import java.sql.SQLException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.sql.DataSource

class DatabaseService {
    companion object {
        val dataSource: DataSource = try {
            ClickHouseDataSource(System.getenv("CLICKHOUSE_URL"))
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }
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
        addr: String
    ) {
        sessionOf(dataSource).use { session ->
            session.execute(
                queryOf(
                    """
    INSERT INTO country_days_tracker_bot.country_days_tracker
    (date_time, latitude, longitude, country, tzname, alt, batt, acc, vac, conn, locality, ghash, p, addr)
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
        toString(?) AS addr
    """,
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
                    addr
                )

            )
        }
    }


    fun getCountryStats(): List<Pair<String, Int>> {
        return sessionOf(dataSource)
            .run(
                queryOf(
                    """
                    SELECT country, count(*) as count_of_days
                    FROM (
                      SELECT DISTINCT lower(country) as country,toStartOfDay(date_time)
                      FROM country_days_tracker_bot.country_days_tracker
                    )
                    GROUP BY country
                    ORDER BY count(*) DESC
                    """,
                    mapOf(),
                )
                    .map { row ->
                        Pair(row.string("country"), row.int("count_of_days"))
                    }
                    .asList
            )
    }

    fun getTrips(countryName: String): List<Triple<String, Pair<LocalDate, LocalDate>, Int>> {
        return sessionOf(dataSource)
            .run(
                queryOf(
                    """
                    WITH
                    -- Remove time, keep only date
                    data AS (
                        SELECT
                            user_id,
                            toDate(date_time) AS day,
                            country
                        FROM country_days_tracker_bot.country_days_tracker
                        WHERE user_id = :user_id
                        AND lower(country) = lower(:country_name)
                        GROUP BY
                            user_id, day, country
                    ),

                    -- Define session boundaries by continuous dates
                    with_sessions AS (
                        SELECT
                            *,
                            day - toIntervalDay(row_number() OVER (PARTITION BY user_id, country ORDER BY day)) AS session_id
                        FROM data
                    ),

                    -- Group by sessions, count duration
                    sessions_grouped AS (
                        SELECT
                            user_id,
                            country,
                            min(day) AS start_day,
                            max(day) AS end_day,
                            count() AS days_in_country
                        FROM with_sessions
                        GROUP BY
                            user_id, country, session_id
                    )

                    -- Output all sessions for the specified country
                    SELECT
                        country,
                        start_day,
                        end_day,
                        days_in_country
                    FROM sessions_grouped
                    WHERE user_id = :user_id
                    ORDER BY start_day DESC
                    """,
                    mapOf(
                        "country_name" to countryName
                    )
                )
                    .map { row ->
                        Triple(
                            row.string("country"),
                            Pair(row.localDate("start_day"), row.localDate("end_day")),
                            row.int("days_in_country")
                        )
                    }
                    .asList
            )
    }

    fun getCurrentCountryLength(): Pair<String, Int> {
        return sessionOf(dataSource)
            .run(
                queryOf(
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
            min(day) AS start_day,
            max(day) AS end_day,
            count() AS days_in_country
        FROM with_sessions
        GROUP BY country, session_id
    )

SELECT
    country,
    days_in_country
FROM (
    SELECT *,
           row_number() OVER (ORDER BY end_day DESC) AS rn
    FROM sessions_grouped
)
WHERE rn = 1
 
                """, mapOf()
            ).map { row -> Pair(row.string("country"), row.int("days_in_country")) }.asSingle
            )!!
    }
}
