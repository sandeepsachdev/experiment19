package com.example.freetotv.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    // "Now" fixed at 08:00 UTC on 2026-06-10 so airings later that day are upcoming.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-10T08:00:00Z"), ZoneOffset.UTC);

    private TvMazeClient tvMazeClient;
    private OmdbClient omdbClient;
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        tvMazeClient = mock(TvMazeClient.class);
        omdbClient = mock(OmdbClient.class);
        AppProperties properties = new AppProperties(null, null, null, null);
        service = new RecommendationService(tvMazeClient, omdbClient,
                new ScoringService(properties), properties, CLOCK);
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

    private List<Recommendation> recommend(double minRating, Optional<String> genre) {
        return service.recommend(new RecommendationRequest("GB", 1, 25, minRating, genre));
    }

    @Test
    void dedupesShowsAndAggregatesUpcomingAirings() {
        when(tvMazeClient.getSchedule(eq("GB"), any(LocalDate.class))).thenReturn(List.of(
                entry(42, "Brilliant Drama", 8.6, List.of("Drama"), "BBC One", "2026-06-10T20:00:00+01:00"),
                entry(42, "Brilliant Drama", 8.6, List.of("Drama"), "BBC Two", "2026-06-10T22:00:00+01:00"),
                entry(7, "Okay Comedy", 7.0, List.of("Comedy"), "ITV", "2026-06-10T21:00:00+01:00")));

        List<Recommendation> recs = recommend(0.0, Optional.empty());

        assertThat(recs).hasSize(2);
        Recommendation drama = recs.get(0);
        assertThat(drama.title()).isEqualTo("Brilliant Drama");
        assertThat(drama.airings()).hasSize(2);
        // Airings are sorted soonest-first and formatted for scanning.
        assertThat(drama.airings().get(0).channel()).isEqualTo("BBC One");
        assertThat(drama.airings().get(0).dayLabel()).isEqualTo("Today");
        assertThat(drama.airings().get(0).time()).isEqualTo("20:00");
        assertThat(drama.summary()).isEqualTo("Summary & more.");
    }

    @Test
    void ordersBySoonestUpcomingAiringNotByScore() {
        // The lower-rated show airs first and should therefore come first.
        when(tvMazeClient.getSchedule(eq("GB"), any(LocalDate.class))).thenReturn(List.of(
                entry(42, "Top Rated, Later", 9.5, List.of("Drama"), "BBC One", "2026-06-10T22:00:00+01:00"),
                entry(7, "Lower Rated, Sooner", 6.0, List.of("Comedy"), "ITV", "2026-06-10T19:00:00+01:00")));

        List<Recommendation> recs = recommend(0.0, Optional.empty());

        assertThat(recs).extracting(Recommendation::title)
                .containsExactly("Lower Rated, Sooner", "Top Rated, Later");
    }

    @Test
    void hidesAiringsAlreadyInThePast() {
        when(tvMazeClient.getSchedule(eq("GB"), any(LocalDate.class))).thenReturn(List.of(
                // 06:00Z is before the fixed "now" of 08:00Z -> excluded entirely.
                entry(42, "Already Finished", 9.0, List.of("Drama"), "BBC One", "2026-06-10T06:00:00+00:00"),
                entry(7, "Coming Up", 7.0, List.of("Comedy"), "ITV", "2026-06-10T20:00:00+00:00")));

        List<Recommendation> recs = recommend(0.0, Optional.empty());

        assertThat(recs).extracting(Recommendation::title).containsExactly("Coming Up");
    }

    @Test
    void dropsShowWhenAllAiringsArePast() {
        when(tvMazeClient.getSchedule(eq("GB"), any(LocalDate.class))).thenReturn(List.of(
                entry(42, "Only Aired Earlier", 9.0, List.of("Drama"), "BBC One", "2026-06-10T07:30:00+00:00")));

        assertThat(recommend(0.0, Optional.empty())).isEmpty();
    }

    @Test
    void filtersByGenre() {
        when(tvMazeClient.getSchedule(eq("GB"), any(LocalDate.class))).thenReturn(List.of(
                entry(42, "Brilliant Drama", 8.6, List.of("Drama"), "BBC One", "2026-06-10T20:00:00+01:00"),
                entry(7, "Okay Comedy", 7.0, List.of("Comedy"), "ITV", "2026-06-10T21:00:00+01:00")));

        List<Recommendation> recs = recommend(0.0, Optional.of("drama"));

        assertThat(recs).extracting(Recommendation::title).containsExactly("Brilliant Drama");
    }

    @Test
    void filtersByMinRating() {
        when(tvMazeClient.getSchedule(eq("GB"), any(LocalDate.class))).thenReturn(List.of(
                entry(42, "Brilliant Drama", 8.6, List.of("Drama"), "BBC One", "2026-06-10T20:00:00+01:00"),
                entry(7, "Okay Comedy", 7.0, List.of("Comedy"), "ITV", "2026-06-10T21:00:00+01:00")));

        List<Recommendation> recs = recommend(8.0, Optional.empty());

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

        List<Recommendation> recs = recommend(0.0, Optional.empty());

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).ratingSources()).extracting("name")
                .contains(ScoringService.IMDB, ScoringService.ROTTEN_TOMATOES,
                        ScoringService.METACRITIC, ScoringService.TVMAZE);
    }
}
