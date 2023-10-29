package me.centralhardware.znatoki.telegram.country.days.tracker;

import uk.recurse.geocoding.reverse.Country;
import uk.recurse.geocoding.reverse.ReverseGeocoder;

public class CountryIdentifier {

    private static final ReverseGeocoder geocoder = new ReverseGeocoder();
    public static String identify(Float latitude, Float longitude){
        return geocoder.getCountry(latitude, longitude)
                .map(Country::name)
                .orElseThrow(() -> new IllegalArgumentException(""));
    }

}
