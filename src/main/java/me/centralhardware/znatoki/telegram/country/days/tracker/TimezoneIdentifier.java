package me.centralhardware.znatoki.telegram.country.days.tracker;

import java.time.ZoneId;
import java.util.TimeZone;

public class TimezoneIdentifier {

    public static ZoneId identify(String ts) {
        return TimeZone.getTimeZone(ts).toZoneId();
    }

}
