package me.centralhardware.znatoki.telegram.country.days.tracker

import me.centralhardware.znatoki.telegram.country.days.tracker.Dto.Track
import org.apache.ibatis.annotations.*

@Mapper
interface CountryDaysTrackerMapper {

    @Insert("""
            INSERT INTO country_days_tracker
            (
                date_time,
                user_id,
                latitude,
                longitude,
                altitude,
                country
            )
            VALUES (
                #{track.dateTime},
                #{track.userId},
                #{track.latitude},
                #{track.longitude},
                #{track.altitude},
                #{track.country}
            )
            """)
    fun __insert(@Param("track") track: Track)

    @Select("""
            SELECT country, count(*) as count_of_days
            FROM (
                     SELECT DISTINCT country,toStartOfDay(date_time)
                     FROM country_days_tracker
                     WHERE user_id = 428985392
                     )
            GROUP BY country
            ORDER BY count(*) DESC
            """)
    @Results(
        Result(property = "stat", column = "country"),
        Result(property = "value", column = "count_of_days")
    )
    fun __getStat(@Param("user_id") userId: Long): List<Pair<String, Int>>

    companion object {
        val mapper = ClickhouseConfiguration.sqlSessionClickhouse.openSession().getMapper(CountryDaysTrackerMapper::class.java)

        fun insert(track: Track) {
            mapper.__insert(track)
        }

        fun getStat(userId: Long): List<Pair<String, Int>> {
            return mapper.__getStat(userId)
        }
    }
}