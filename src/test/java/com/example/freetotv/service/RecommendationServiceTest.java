package com.example.freetotv.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.example.freetotv.client.omdb.OmdbClient;
import com.example.freetotv.client.omdb.dto.OmdbResponse;
import com.example.freetotv.client.tvmaze.TvMazeClient;
import com.example.freetotv.client.tvmaze.dto.ScheduleEntry;
import com.example.freetotv.client.tvmaze.dto.TvMazeShow;
import com.example.freetotv.config.AppProperties;
import com.example.freetotv.domain.Recommendation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {

    private TvMazeClient tvMazeClient;
    private OmdbClient omdbClient;
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        tvMazeClient = mock(TvMazeClient.class);
        omdbClient = mock(OmdbClient.class);
        AppProperties properties = new AppProperties(null, null, null, null);
        service = new RecommendationService(tvMazeClient, omdbClient,
                new ScoringService(properties), properties);
        when(omdbClient.isEnabled()).thenReturn(false);
    }

    private ScheduleEntry entry(long showId, String name, Double rating, List<String> genres,
                                String channel, String airstamp) {
        TvMazeShow show = new TvMazeShow(
                showId, name, "Scripted", "English", genres, "Running",
                new TvMazeShow.Rating(rating),
                new TvMazeShow.Network(1L, channel, new TvMazeShow.Country("United Kingdom", "GB")),
                null, "<p>Summary &amp; more.</p>", "http://site", null,
                new TvMazeShow.ExternalIds("tt" + showId, null, null));
        return new ScheduleEntry(showId * 100, name + " ep", 1, 1, "2026-06-10", "20:00",
                airstamp, 60, show);
    }

    @Test
    void dedupesShowsAndAggregatesAirings() {
        when(tvMazeClient.getSchedule(eq("GB"), any(LocalDate.class))).thenReturn(List.of(
                entry(42, "Brilliant Drama", 8.6, List.of("Drama"), "BBC One", "2026-06-10T20:00:00+01:00"),
                entry(42, "Brilliant Drama", 8.6, List.of("Drama"), "BBC Two", "2026-06-10T22:00:00+01:00"),
                entry(7, "Okay Comedy", 7.0, List.of("Comedy"), "ITV", "2026-06-10T21:00:00+01:00")));

        List<Recommendation> recs = service.recommend(
                new RecommendationRequest("GB", 1, 25, 0.0, Optional.empty()));

        assertThat(recs).hasSize(2);
        Recommendation top = recs.get(0);
        assertThat(top.title()).isEqualTo("Brilliant Drama");
        assertThat(top.airings()).hasSize(2);
        // Aggregated airings are sorted soonest-first.
        assertThat(top.airings().get(0).channel()).isEqualTo("BBC One");
        assertThat(top.summary()).isEqualTo("Summary & more.");
        // Ranked by composite score: the higher-rated drama comes first.
        assertThat(recs.get(0).compositeScore()).isGreaterThan(recs.get(1).compositeScore());
    }

    @Test
    void filtersByGenre() {
        when(tvMazeClient.getSchedule(eq("GB"), any(LocalDate.class))).thenReturn(List.of(
                entry(42, "Brilliant Drama", 8.6, List.of("Drama"), "BBC One", "2026-06-10T20:00:00+01:00"),
                entry(7, "Okay Comedy", 7.0, List.of("Comedy"), "ITV", "2026-06-10T21:00:00+01:00")));

        List<Recommendation> recs = service.recommend(
                new RecommendationRequest("GB", 1, 25, 0.0, Optional.of("drama")));

        assertThat(recs).extracting(Recommendation::title).containsExactly("Brilliant Drama");
    }

    @Test
    void filtersByMinRating() {
        when(tvMazeClient.getSchedule(eq("GB"), any(LocalDate.class))).thenReturn(List.of(
                entry(42, "Brilliant Drama", 8.6, List.of("Drama"), "BBC One", "2026-06-10T20:00:00+01:00"),
                entry(7, "Okay Comedy", 7.0, List.of("Comedy"), "ITV", "2026-06-10T21:00:00+01:00")));

        List<Recommendation> recs = service.recommend(
                new RecommendationRequest("GB", 1, 25, 8.0, Optional.empty()));

        assertThat(recs).extracting(Recommendation::title).containsExactly("Brilliant Drama");
    }

    @Test
    void enrichesTopCandidatesWhenOmdbEnabled() {
        when(omdbClient.isEnabled()).thenReturn(true);
        when(omdbClient.lookupByImdbId("tt42")).thenReturn(Optional.of(
                new OmdbResponse("True", null, "Brilliant Drama", "9.0", "85",
                        List.of(new OmdbResponse.Rating("Rotten Tomatoes", "95%")))));
        when(tvMazeClient.getSchedule(eq("GB"), any(LocalDate.class))).thenReturn(List.of(
                entry(42, "Brilliant Drama", 8.6, List.of("Drama"), "BBC One", "2026-06-10T20:00:00+01:00")));

        List<Recommendation> recs = service.recommend(
                new RecommendationRequest("GB", 1, 25, 0.0, Optional.empty()));

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).ratingSources()).extracting("name")
                .contains(ScoringService.IMDB, ScoringService.ROTTEN_TOMATOES,
                        ScoringService.METACRITIC, ScoringService.TVMAZE);
    }
}
