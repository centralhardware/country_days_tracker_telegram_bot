package me.centralhardware.znatoki.telegram.country.days.tracker;

import net.iakovlev.timeshape.TimeZoneEngine;

import java.time.ZoneId;

public class TimezoneIdentifier {

    private final static TimeZoneEngine engine = TimeZoneEngine.initialize();

    public static ZoneId identify(Float latitude, Float longitude) {
        return engine.query(latitude, longitude)
                .map(it -> {
                    System.out.printf("Identified timezone %s \n", it.getId());
                    return it;
                }).orElseThrow().normalized();


    }

}
