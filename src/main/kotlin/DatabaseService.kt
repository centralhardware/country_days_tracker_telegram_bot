
import com.clickhouse.jdbc.ClickHouseDataSource
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import kotliquery.queryOf
import kotliquery.sessionOf
import java.sql.SQLException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
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
        latitude: Float, 
        longitude: Float, 
        ts: ZoneId, 
        country: String, 
        userId: Long,
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
        KSLog.info("lat: $latitude, lon: $longitude, ts: $ts, cc: $country, " +
                "alt: $alt, batt: $batt, acc: $acc, vac: $vac, conn: $conn, " +
                "locality: $locality, ghash: $ghash, p: $p, addr: $addr")

        sessionOf(dataSource)
            .execute(
                queryOf(
                    """
                    INSERT INTO country_days_tracker_bot.country_days_tracker
                    ( date_time,
                      user_id,
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
                      addr
                    )
                    VALUES (
                      CAST(? AS DateTime),
                      CAST(? AS Int64),
                      CAST(? AS Float32),
                      CAST(? AS Float32),
                      CAST(? AS String),
                      CAST(? AS String),
                      CAST(? AS UInt16),
                      CAST(? AS UInt8),
                      CAST(? AS UInt8),
                      CAST(? AS UInt8),
                      CAST(? AS String),
                      CAST(? AS String),
                      CAST(? AS String),
                      CAST(? AS Float64),
                      CAST(? AS String)
                    )
                    """,
                    listOf(
                        ZonedDateTime.now().withZoneSameInstant(ts).toLocalDateTime(),
                        userId,
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
                    ),
                )
            )
    }

    fun getCountryStats(userId: Long): List<Pair<String, Int>> {
        return sessionOf(dataSource)
            .run(
                queryOf(
                    """
                    SELECT country, count(*) as count_of_days
                    FROM (
                      SELECT DISTINCT lower(country) as country,toStartOfDay(date_time)
                      FROM country_days_tracker_bot.country_days_tracker
                      WHERE user_id = :user_id
                    )
                    GROUP BY country
                    ORDER BY count(*) DESC
                    """,
                    mapOf("user_id" to userId),
                )
                .map { row ->
                    Pair(row.string("country"), row.int("count_of_days"))
                }
                .asList
            )
    }

    fun getTrips(userId: Long, countryName: String): List<Triple<String, Pair<LocalDate, LocalDate>, Int>> {
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
                        "user_id" to userId,
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
            .run(queryOf(
                """
                WITH
                -- Remove time, keep only date
                data AS (
                    SELECT
                        user_id,
                        toDate(date_time) AS day,
                        country
                    FROM country_days_tracker_bot.country_days_tracker
                    GROUP BY
                        user_id, day, country
                ),

                -- Define session boundaries by country change
                with_sessions AS (
                    SELECT
                        *,
                        row_number() OVER (PARTITION BY user_id ORDER BY day) -
                        row_number() OVER (PARTITION BY user_id, country ORDER BY day) AS session_id
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

                -- Output only the current session (maximum by date)
                SELECT
                    country,
                    days_in_country
                FROM (
                     SELECT *,
                            row_number() OVER (PARTITION BY user_id ORDER BY end_day DESC) AS rn
                     FROM sessions_grouped
                     )
                WHERE rn = 1 
                """, mapOf()
            ).map { row -> Pair(row.string("country"), row.int("days_in_country")) }.asSingle)!!
    }
}
