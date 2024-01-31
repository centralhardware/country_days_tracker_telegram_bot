package me.centralhardware.znatoki.telegram.country.days.tracker;

import net.iakovlev.timeshape.TimeZoneEngine;

import java.time.ZoneId;
import java.util.TimeZone;

public class TimezoneIdentifier {

    private final static TimeZoneEngine engine = TimeZoneEngine.initialize();

    public static ZoneId identify(String ts) {
        return TimeZone.getTimeZone(ts).toZoneId();
    }

}
