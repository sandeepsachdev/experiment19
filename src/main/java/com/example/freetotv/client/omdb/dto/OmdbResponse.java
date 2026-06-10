package com.example.freetotv.client.omdb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Subset of the OMDb "by id" response. OMDb returns numeric fields as strings and uses a
 * {@code Response} flag of "True"/"False" to signal success.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OmdbResponse(
        @JsonProperty("Response") String response,
        @JsonProperty("Error") String error,
        @JsonProperty("Title") String title,
        @JsonProperty("imdbRating") String imdbRating,
        @JsonProperty("Metascore") String metascore,
        @JsonProperty("Ratings") List<Rating> ratings) {

    public boolean isSuccess() {
        return "True".equalsIgnoreCase(response);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rating(
            @JsonProperty("Source") String source,
            @JsonProperty("Value") String value) {
    }
}
