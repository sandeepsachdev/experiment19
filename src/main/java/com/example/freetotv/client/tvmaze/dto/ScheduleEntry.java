package com.example.freetotv.client.tvmaze.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single entry in the TVmaze {@code /schedule} response: one episode airing at a point in time.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScheduleEntry(
        Long id,
        String name,
        Integer season,
        Integer number,
        String airdate,
        String airtime,
        String airstamp,
        Integer runtime,
        TvMazeShow show) {
}
