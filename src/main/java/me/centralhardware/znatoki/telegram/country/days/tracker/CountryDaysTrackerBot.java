package me.centralhardware.znatoki.telegram.country.days.tracker;

import com.google.maps.errors.ApiException;
import lombok.Getter;
import me.centralhardware.znatoki.telegram.country.days.tracker.Dto.Track;
import org.apache.commons.lang3.StringUtils;
import org.ocpsoft.prettytime.PrettyTime;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Getter
public class CountryDaysTrackerBot extends TelegramLongPollingBot {

    public CountryDaysTrackerBot() throws TelegramApiException {
        var botApi = new TelegramBotsApi(DefaultBotSession.class);
        botApi.registerBot(this);
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (!update.hasMessage() || StringUtils.isBlank(update.getMessage().getText())) return;

            var text = update.getMessage().getText();
            var userId = update.getMessage().getFrom().getId();

            var i = new AtomicInteger(1);
            if (text.equalsIgnoreCase("/stat")){
                var stat = CountryDaysTrackerMapper.getStat(userId)
                        .stream()
                        .map(it -> i.getAndIncrement() + "- " + it.getStat() + " - " + it.getValue() + prettyDays(it.getValue()))
                        .collect(Collectors.joining("\n"));
                execute(SendMessage.builder()
                        .chatId(userId)
                        .text(stat)
                        .build());
                return;
            }

            Float latitude = round(Float.valueOf(text.split(" ")[0]), 5);
            Float longitude = round(Float.valueOf(text.split(" ")[1]), 5);
            Integer altitude = Integer.valueOf(text.split(" ")[2].replace(",", ".").split("\\.")[0]);

            System.out.printf("lat: %s, lon: %s, alt: %s \n", latitude, longitude, altitude);

            var country = CountryIdentifier.identify(latitude, longitude);

            CountryDaysTrackerMapper.insert(Track
                    .builder()
                    .dateTime(ZonedDateTime.now().withZoneSameInstant(TimezoneIdentifier.identify(latitude, longitude)).toLocalDateTime())
                    .userId(userId)
                    .latitude(latitude)
                    .longitude(longitude)
                    .altitude(altitude)
                    .country(country)
                    .build());
            execute(SendMessage.builder()
                    .chatId(userId)
                    .text(country)
                    .build());
        } catch (TelegramApiException | IOException | InterruptedException | ApiException e) {
            throw new RuntimeException(e);
        }
    }

    private Float round(Float f, Integer scale){
        return BigDecimal.valueOf(f).setScale(scale, RoundingMode.HALF_UP).floatValue();
    }

    private String prettyDays(Integer countOfDays){
        if (countOfDays < 7) return "";

        PrettyTime prettyTime = new PrettyTime();
        prettyTime.setLocale(Locale.US);
        return " (" + prettyTime.formatDuration(LocalDateTime.now().plusDays(countOfDays)) + ")";
    }

    private final String botUsername = System.getenv("BOT_USERNAME");
    private final String botToken = System.getenv("BOT_TOKEN");

}
