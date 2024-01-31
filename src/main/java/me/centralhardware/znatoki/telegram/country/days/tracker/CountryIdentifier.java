package me.centralhardware.znatoki.telegram.country.days.tracker;

import uk.recurse.geocoding.reverse.ReverseGeocoder;

import java.util.Locale;

public class CountryIdentifier {

    private static final ReverseGeocoder geocoder = new ReverseGeocoder();
    public static String identify(String cc){
        return Locale.of("", cc).getDisplayCountry();
    }

}
