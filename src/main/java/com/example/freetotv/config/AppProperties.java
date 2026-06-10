package com.example.freetotv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration for the recommender, bound from the {@code tv.*} namespace.
 */
@ConfigurationProperties(prefix = "tv")
public record AppProperties(
        Defaults defaults,
        TvMaze tvmaze,
        Omdb omdb,
        Scoring scoring) {

    public AppProperties {
        if (defaults == null) {
            defaults = new Defaults("Australia", 3, 25, 0.0);
        }
        if (tvmaze == null) {
            tvmaze = new TvMaze("https://api.tvmaze.com");
        }
        if (omdb == null) {
            omdb = new Omdb(false, "https://www.omdbapi.com", "", 40);
        }
        if (scoring == null) {
            scoring = new Scoring(0.40, 0.25, 0.20, 0.15);
        }
    }

    /** Default request parameters used when the caller does not supply them. */
    public record Defaults(String country, int days, int limit, double minRating) {
    }

    /** TVmaze schedule API settings. */
    public record TvMaze(String baseUrl) {
    }

    /**
     * OMDb enrichment settings. Disabled unless an API key is supplied (typically via the
     * {@code OMDB_API_KEY} environment variable). Only the top {@code maxLookups} candidates
     * are enriched per request to stay within free-tier limits.
     */
    public record Omdb(boolean enabled, String baseUrl, String apiKey, int maxLookups) {
    }

    /**
     * Relative weights for each rating source when computing the composite 0-10 score.
     * Weights are re-normalised across whichever sources are actually present for a title.
     */
    public record Scoring(double imdb, double rottenTomatoes, double metacritic, double tvmaze) {
    }
}
