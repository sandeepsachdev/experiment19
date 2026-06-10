package com.example.freetotv.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.example.freetotv.client.omdb.OmdbClient;
import com.example.freetotv.client.tvmaze.TvMazeClient;
import com.example.freetotv.client.tvmaze.dto.ScheduleEntry;
import com.example.freetotv.client.tvmaze.dto.TvMazeShow;
import com.example.freetotv.config.AppProperties;
import com.example.freetotv.domain.Airing;
import com.example.freetotv.domain.RatingSource;
import com.example.freetotv.domain.Recommendation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Builds free-to-air TV recommendations by aggregating the broadcast schedule across the
 * requested window, enriching the strongest candidates with review-site ratings, scoring them,
 * and returning the best picks.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    // Best-effort mapping from country to a display time zone; falls back to UTC.
    private static final Map<String, ZoneId> COUNTRY_ZONES = Map.of(
            "GB", ZoneId.of("Europe/London"),
            "IE", ZoneId.of("Europe/Dublin"),
            "US", ZoneId.of("America/New_York"),
            "CA", ZoneId.of("America/Toronto"),
            "AU", ZoneId.of("Australia/Sydney"),
            "NZ", ZoneId.of("Pacific/Auckland"),
            "DE", ZoneId.of("Europe/Berlin"),
            "FR", ZoneId.of("Europe/Paris"),
            "ES", ZoneId.of("Europe/Madrid"),
            "IN", ZoneId.of("Asia/Kolkata"));

    private final TvMazeClient tvMazeClient;
    private final OmdbClient omdbClient;
    private final ScoringService scoringService;
    private final AppProperties properties;

    public RecommendationService(TvMazeClient tvMazeClient, OmdbClient omdbClient,
                                 ScoringService scoringService, AppProperties properties) {
        this.tvMazeClient = tvMazeClient;
        this.omdbClient = omdbClient;
        this.scoringService = scoringService;
        this.properties = properties;
    }

    public List<Recommendation> recommend(RecommendationRequest request) {
        ZoneId zone = COUNTRY_ZONES.getOrDefault(request.country().toUpperCase(Locale.ROOT), ZoneId.of("UTC"));
        LocalDate today = LocalDate.now(zone);

        // 1. Aggregate the schedule across the window, grouped by show.
        Map<Long, ShowAggregate> byShow = new LinkedHashMap<>();
        for (int dayOffset = 0; dayOffset < request.days(); dayOffset++) {
            LocalDate date = today.plusDays(dayOffset);
            for (ScheduleEntry entry : tvMazeClient.getSchedule(request.country(), date)) {
                accumulate(byShow, entry);
            }
        }
        log.debug("Aggregated {} distinct shows for {} over {} day(s)",
                byShow.size(), request.country(), request.days());

        // 2. Rank candidates by their crowd rating so we only enrich the most promising ones.
        List<ShowAggregate> candidates = new ArrayList<>(byShow.values());
        candidates.sort(Comparator.comparingDouble(ShowAggregate::tvmazeScore).reversed());

        // 3. Enrich the top candidates with review-site ratings, then build recommendations.
        List<Recommendation> recommendations = new ArrayList<>();
        int enrichBudget = omdbClient.isEnabled() ? properties.omdb().maxLookups() : 0;
        int enriched = 0;
        for (ShowAggregate aggregate : candidates) {
            boolean tryEnrich = enriched < enrichBudget && StringUtils.hasText(aggregate.imdbId());
            Recommendation rec = toRecommendation(aggregate, tryEnrich, zone);
            if (tryEnrich) {
                enriched++;
            }
            recommendations.add(rec);
        }

        // 4. Filter, sort by composite score, and cap to the requested limit.
        String genreFilter = request.genre().map(g -> g.toLowerCase(Locale.ROOT)).orElse(null);
        return recommendations.stream()
                .filter(r -> r.compositeScore() >= request.minRating())
                .filter(r -> matchesGenre(r, genreFilter))
                .sorted(Comparator.comparingDouble(Recommendation::compositeScore).reversed()
                        .thenComparing(earliestStart()))
                .limit(request.limit())
                .toList();
    }

    private void accumulate(Map<Long, ShowAggregate> byShow, ScheduleEntry entry) {
        TvMazeShow show = entry.show();
        if (show == null || show.id() == null) {
            return;
        }
        ShowAggregate aggregate = byShow.computeIfAbsent(show.id(), id -> new ShowAggregate(show));
        aggregate.addAiring(toAiring(entry, show));
    }

    private Airing toAiring(ScheduleEntry entry, TvMazeShow show) {
        String channel = show.network() != null ? show.network().name()
                : (show.webChannel() != null ? show.webChannel().name() : null);
        return new Airing(channel, parseStart(entry.airstamp()), entry.name(),
                entry.season(), entry.number(), entry.runtime());
    }

    private OffsetDateTime parseStart(String airstamp) {
        if (!StringUtils.hasText(airstamp)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(airstamp);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private Recommendation toRecommendation(ShowAggregate aggregate, boolean enrich, ZoneId zone) {
        TvMazeShow show = aggregate.show();

        List<RatingSource> sources = new ArrayList<>();
        scoringService.tvmazeRating(show.rating() != null ? show.rating().average() : null)
                .ifPresent(sources::add);
        if (enrich) {
            omdbClient.lookupByImdbId(aggregate.imdbId())
                    .ifPresent(omdb -> sources.addAll(scoringService.omdbRatings(omdb)));
        }

        double composite = scoringService.composite(sources);
        List<Airing> airings = aggregate.sortedAirings();
        Optional<Airing> next = airings.stream().filter(a -> a.start() != null).findFirst();
        String why = scoringService.buildWhy(show.name(), show.genres(), sources, next, zone);

        return new Recommendation(
                show.id(),
                show.name(),
                show.type(),
                show.genres() == null ? List.of() : show.genres(),
                stripHtml(show.summary()),
                imageUrl(show),
                show.officialSite(),
                composite,
                sources,
                airings,
                why);
    }

    private boolean matchesGenre(Recommendation rec, String genreFilter) {
        if (genreFilter == null) {
            return true;
        }
        return rec.genres().stream().anyMatch(g -> g.toLowerCase(Locale.ROOT).contains(genreFilter));
    }

    private Comparator<Recommendation> earliestStart() {
        return Comparator.comparing(
                r -> r.airings().stream()
                        .map(Airing::start)
                        .filter(s -> s != null)
                        .min(Comparator.naturalOrder())
                        .orElse(OffsetDateTime.MAX),
                Comparator.naturalOrder());
    }

    private String imageUrl(TvMazeShow show) {
        if (show.image() == null) {
            return null;
        }
        return StringUtils.hasText(show.image().original()) ? show.image().original() : show.image().medium();
    }

    private String stripHtml(String html) {
        if (!StringUtils.hasText(html)) {
            return null;
        }
        return html.replaceAll("<[^>]+>", "").replace("&amp;", "&")
                .replaceAll("\\s+", " ").trim();
    }

    /** Mutable accumulator used while grouping schedule entries by show. */
    private static final class ShowAggregate {
        private final TvMazeShow show;
        private final List<Airing> airings = new ArrayList<>();

        ShowAggregate(TvMazeShow show) {
            this.show = show;
        }

        TvMazeShow show() {
            return show;
        }

        void addAiring(Airing airing) {
            airings.add(airing);
        }

        List<Airing> sortedAirings() {
            return airings.stream()
                    .sorted(Comparator.comparing(Airing::start,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        }

        double tvmazeScore() {
            return show.rating() != null && show.rating().average() != null ? show.rating().average() : 0.0;
        }

        String imdbId() {
            return show.externals() != null ? show.externals().imdb() : null;
        }
    }
}
