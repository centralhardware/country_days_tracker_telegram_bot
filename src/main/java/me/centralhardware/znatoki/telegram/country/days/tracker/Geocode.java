package me.centralhardware.znatoki.telegram.country.days.tracker;

import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.AddressType;
import com.google.maps.model.LatLng;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.util.Arrays;

public class Geocode {

    private final static GeoApiContext context = new GeoApiContext.Builder()
            .apiKey(System.getenv("GEOCODE_API_KEY"))
            .build();

    public static String geocode(Float latitude, Float longitude) throws IOException, InterruptedException, ApiException {
        var res = GeocodingApi.reverseGeocode(context, new LatLng(latitude, longitude)).await();
        var address = res[0].formattedAddress;

        System.out.printf("Geocoded address  %s for coordinate lat: %s lon %s%n",
                address,
                latitude,
                longitude);

        return address;
    }

}
