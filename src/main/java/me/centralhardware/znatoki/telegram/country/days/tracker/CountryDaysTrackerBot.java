package me.centralhardware.znatoki.telegram.country.days.tracker;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.time.LocalDateTime;

@Getter
public class CountryDaysTrackerBot extends TelegramLongPollingBot {

    public CountryDaysTrackerBot() throws TelegramApiException {
        var botApi = new TelegramBotsApi(DefaultBotSession.class);
        botApi.registerBot(this);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || StringUtils.isBlank(update.getMessage().getText())) return;

        var text = update.getMessage().getText();

        Float latitude = Float.valueOf(text.split(" ")[0]);
        Float longitude = Float.valueOf(text.split(" ")[1]);
        var country = CountryIdentifier.identify(latitude, longitude);

        CountryDaysTrackerMapper.insert(Track
                .builder()
                .dateTime(LocalDateTime.now())
                .userId(update.getMessage().getFrom().getId())
                .latitude(latitude)
                .longitude(longitude)
                .country(country)
                .build());

        var message = SendMessage.builder()
                .chatId(update.getMessage().getChatId())
                .text(country)
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private final String botUsername = System.getenv("BOT_USERNAME");
    private final String botToken = System.getenv("BOT_TOKEN");

}
