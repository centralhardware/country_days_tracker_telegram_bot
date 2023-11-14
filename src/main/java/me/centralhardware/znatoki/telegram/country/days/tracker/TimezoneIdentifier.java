package me.centralhardware.znatoki.telegram.country.days.tracker;

import com.google.maps.GeoApiContext;
import com.google.maps.TimeZoneApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.LatLng;

import java.io.IOException;
import java.time.ZoneId;

public class TimezoneIdentifier {

    private final static GeoApiContext context = new GeoApiContext.Builder()
            .apiKey(System.getenv("GEOCODE_API_KEY"))
            .build();
    public static ZoneId identify(Float latitude, Float longitude) throws IOException, InterruptedException, ApiException {
        var timezone = TimeZoneApi.getTimeZone(context, new LatLng(latitude, longitude)).await();
        System.out.printf("Identified timezone %s \n", timezone.getID());
        return timezone.toZoneId();
    }

}
