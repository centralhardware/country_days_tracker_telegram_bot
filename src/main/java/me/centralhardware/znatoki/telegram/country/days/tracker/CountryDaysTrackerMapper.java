package me.centralhardware.znatoki.telegram.country.days.tracker;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CountryDaysTrackerMapper {

    @Insert("""
            INSERT INTO country_days_tracker
            (
                date_time,
                user_id,
                latitude,
                longitude,
                country
            )
            VALUES (
                #{track.dateTime},
                #{track.userId},
                #{track.latitude},
                #{track.longitude},
                #{track.country}
            )
            """)
    void __insert(@Param("track") Track track);

    @Select("""
            SELECT country, count(*)
            FROM (
                SELECT DISTINCT country, toStartOfDay(date_time)
                FROM country_days_tracker
                 )
            GROUP BY country
            ORDER BY count(*) DESC
            """)
    @Results({
            @Result(property = "country", column = "country"),
            @Result(property = "countOfDays", column = "count_of_days")
    })
    List<Stat> __getStat();

    CountryDaysTrackerMapper mapper = ClickhouseConfiguration.getSqlSessionClickhouse().openSession().getMapper(CountryDaysTrackerMapper.class);
    static void insert(Track track){
        mapper.__insert(track);
    }

    static List<Stat> getStat(){
        return mapper.__getStat();
    }


}
