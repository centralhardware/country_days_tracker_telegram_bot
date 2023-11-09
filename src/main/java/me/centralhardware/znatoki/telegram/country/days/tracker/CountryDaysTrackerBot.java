package me.centralhardware.znatoki.telegram.country.days.tracker;

import com.google.maps.errors.ApiException;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.time.LocalDateTime;
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

            if (text.equalsIgnoreCase("/stat")){
                var stat = CountryDaysTrackerMapper.getStat(userId)
                        .stream()
                        .map(it -> it.getStat() + " - " + it.getValue())
                        .collect(Collectors.joining("\n"));
                execute(SendMessage.builder()
                        .chatId(userId)
                        .text(stat)
                        .build());
                return;
            } else if (text.equalsIgnoreCase("/statAddresses")){
                var stat = CountryDaysTrackerMapper.getStatAddresses(userId)
                        .stream()
                        .map(it -> it.getStat() + " - " + it.getValue())
                        .collect(Collectors.joining("\n"));
                execute(SendMessage.builder()
                        .chatId(userId)
                        .text(stat)
                        .build());
                return;
            }

            Float latitude = Float.valueOf(text.split(" ")[0]);
            Float longitude = Float.valueOf(text.split(" ")[1]);
            Float altitude = Float.valueOf(text.split(" ")[2].replace(",", "."));
            var country = CountryIdentifier.identify(latitude, longitude);
            var address = Geocode.geocode(latitude, longitude);

            System.out.printf("lat: %s, lon: %s, lat: %s", latitude, longitude, altitude);

            CountryDaysTrackerMapper.insert(Track
                    .builder()
                    .dateTime(LocalDateTime.now())
                    .userId(userId)
                    .latitude(latitude)
                    .longitude(longitude)
                    .altitude(altitude)
                    .country(country)
                    .address(address)
                    .build());
            ;
            execute(SendMessage.builder()
                    .chatId(userId)
                    .text(address)
                    .build());
        } catch (TelegramApiException | IOException | InterruptedException | ApiException e) {
            throw new RuntimeException(e);
        }
    }

    private final String botUsername = System.getenv("BOT_USERNAME");
    private final String botToken = System.getenv("BOT_TOKEN");

}
