package com.example.freetotv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Entry point for the free-to-air TV recommender.
 *
 * <p>The app pulls broadcast TV schedules from TVmaze and enriches the strongest candidates
 * with review-site ratings (OMDb / IMDb / Rotten Tomatoes / Metacritic) to suggest the best
 * things to watch today and over the next few days, ordered by what is coming up soonest.
 */
@SpringBootApplication
@EnableCaching
public class FreeToTvRecommenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(FreeToTvRecommenderApplication.class, args);
    }

    /** System clock used for "now" comparisons; overridable in tests for determinism. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
