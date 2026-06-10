package com.example.freetotv.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.example.freetotv.client.omdb.dto.OmdbResponse;
import com.example.freetotv.config.AppProperties;
import com.example.freetotv.domain.Airing;
import com.example.freetotv.domain.RatingSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringServiceTest {

    private final ScoringService scoring = new ScoringService(
            new AppProperties(null, null, null,
                    new AppProperties.Scoring(0.40, 0.25, 0.20, 0.15)));

    @Test
    void compositeBlendsSourcesUsingConfiguredWeights() {
        List<RatingSource> sources = List.of(
                new RatingSource(ScoringService.IMDB, 8.0, "8.0/10"),
                new RatingSource(ScoringService.ROTTEN_TOMATOES, 9.0, "90%"),
                new RatingSource(ScoringService.METACRITIC, 7.0, "70/100"),
                new RatingSource(ScoringService.TVMAZE, 8.0, "8.0/10"));

        // 8*.4 + 9*.25 + 7*.2 + 8*.15 = 8.05 (weights sum to 1.0)
        assertThat(scoring.composite(sources)).isEqualTo(8.05);
    }

    @Test
    void compositeReNormalisesWhenOnlySomeSourcesPresent() {
        List<RatingSource> sources = List.of(
                new RatingSource(ScoringService.IMDB, 8.0, "8.0/10"),
                new RatingSource(ScoringService.TVMAZE, 6.0, "6.0/10"));

        // (8*.4 + 6*.15) / (.4 + .15) = 4.1 / .55 = 7.45...
        assertThat(scoring.composite(sources)).isEqualTo(7.45);
    }

    @Test
    void compositeIsZeroWithNoSources() {
        assertThat(scoring.composite(List.of())).isZero();
    }

    @Test
    void omdbRatingsParsesImdbMetascoreAndRottenTomatoes() {
        OmdbResponse omdb = new OmdbResponse("True", null, "Some Show", "8.4", "74",
                List.of(new OmdbResponse.Rating("Rotten Tomatoes", "92%")));

        List<RatingSource> sources = scoring.omdbRatings(omdb);

        assertThat(sources).extracting(RatingSource::name)
                .containsExactlyInAnyOrder(
                        ScoringService.IMDB, ScoringService.METACRITIC, ScoringService.ROTTEN_TOMATOES);
        assertThat(sources).filteredOn(s -> s.name().equals(ScoringService.IMDB))
                .singleElement().extracting(RatingSource::scoreOutOfTen).isEqualTo(8.4);
        assertThat(sources).filteredOn(s -> s.name().equals(ScoringService.ROTTEN_TOMATOES))
                .singleElement().extracting(RatingSource::scoreOutOfTen).isEqualTo(9.2);
        assertThat(sources).filteredOn(s -> s.name().equals(ScoringService.METACRITIC))
                .singleElement().extracting(RatingSource::scoreOutOfTen).isEqualTo(7.4);
    }

    @Test
    void omdbRatingsSkipsNotAvailableValues() {
        OmdbResponse omdb = new OmdbResponse("True", null, "Some Show", "N/A", "N/A", List.of());
        assertThat(scoring.omdbRatings(omdb)).isEmpty();
    }

    @Test
    void tvmazeRatingBuildsSourceWhenPresent() {
        Optional<RatingSource> source = scoring.tvmazeRating(8.2);
        assertThat(source).isPresent();
        assertThat(source.get().name()).isEqualTo(ScoringService.TVMAZE);
        assertThat(source.get().scoreOutOfTen()).isEqualTo(8.2);
        assertThat(source.get().display()).isEqualTo("8.2/10");
    }

    @Test
    void tvmazeRatingEmptyWhenNull() {
        assertThat(scoring.tvmazeRating(null)).isEmpty();
    }

    @Test
    void buildWhyIncludesRatingsAndAiringTime() {
        ZoneId zone = ZoneOffset.UTC;
        OffsetDateTime now = OffsetDateTime.now(zone);
        Airing airing = new Airing("BBC One", now.plusHours(2), "Episode 1", 1, 1, 60);

        String why = scoring.buildWhy("Great Show", List.of("Drama"),
                List.of(new RatingSource(ScoringService.IMDB, 8.4, "8.4/10")),
                Optional.of(airing), zone);

        assertThat(why).contains("drama");
        assertThat(why).contains("IMDb 8.4/10");
        assertThat(why).contains("airing");
        assertThat(why).contains("BBC One");
    }
}
