package com.example.freetotv.client.tvmaze.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The show associated with a schedule entry.
 *
 * <p>For broadcast TV the {@code network} field is populated (e.g. "BBC One"); streaming-only
 * shows instead populate {@code webChannel}. The {@code /schedule} endpoint only returns
 * network (broadcast) airings, which is what "free-to-air" means here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TvMazeShow(
        Long id,
        String name,
        String type,
        String language,
        List<String> genres,
        String status,
        Rating rating,
        Network network,
        Network webChannel,
        String summary,
        String officialSite,
        ShowImage image,
        ExternalIds externals) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rating(Double average) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Network(Long id, String name, Country country) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Country(String name, String code) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShowImage(String medium, String original) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalIds(String imdb, Integer thetvdb, Integer tvrage) {
    }
}
