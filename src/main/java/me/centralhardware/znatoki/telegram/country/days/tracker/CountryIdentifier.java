package me.centralhardware.znatoki.telegram.country.days.tracker;

import java.util.Locale;

public class CountryIdentifier {

    public static String identify(String cc){
        return Locale.of("", cc).getDisplayCountry();
    }

}
