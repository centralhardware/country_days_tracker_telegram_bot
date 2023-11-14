package me.centralhardware.znatoki.telegram.country.days.tracker.Dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Builder
@Getter
@Setter
public class Track {

    private LocalDateTime dateTime;
    private Long userId;
    private Float latitude;
    private Float longitude;
    private Float altitude;
    private String country;
    private String address;

}
