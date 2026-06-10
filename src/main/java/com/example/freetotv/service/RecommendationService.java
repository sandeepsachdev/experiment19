package com.example.freetotv.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
 * requested window, dropping anything already aired, enriching the strongest candidates with
 * review-site ratings, scoring them, and returning the picks ordered by what is coming up soonest.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    // Best-effort mapping from country to a display time zone; falls back to UTC.
    private static final Map<String, ZoneId> COUNTRY_ZONES = Map.ofEntries(
            Map.entry("AU", ZoneId.of("Australia/Sydney")),
            Map.entry("NZ", ZoneId.of("Pacific/Auckland")),
            Map.entry("GB", ZoneId.of("Europe/London")),
            Map.entry("IE", ZoneId.of("Europe/Dublin")),
            Map.entry("US", ZoneId.of("America/New_York")),
            Map.entry("CA", ZoneId.of("America/Toronto")),
            Map.entry("DE", ZoneId.of("Europe/Berlin")),
            Map.entry("FR", ZoneId.of("Europe/Paris")),
            Map.entry("ES", ZoneId.of("Europe/Madrid")),
            Map.entry("IT", ZoneId.of("Europe/Rome")),
            Map.entry("NL", ZoneId.of("Europe/Amsterdam")),
            Map.entry("IN", ZoneId.of("Asia/Kolkata")));

    private final TvMazeClient tvMazeClient;
    private final OmdbClient omdbClient;
    private final ScoringService scoringService;
    private final AppProperties properties;
    private final Clock clock;

    public RecommendationService(TvMazeClient tvMazeClient, OmdbClient omdbClient,
                                 ScoringService scoringService, AppProperties properties, Clock clock) {
        this.tvMazeClient = tvMazeClient;
        this.omdbClient = omdbClient;
        this.scoringService = scoringService;
        this.properties = properties;
        this.clock = clock;
    }

    public List<Recommendation> recommend(RecommendationRequest request) {
        ZoneId zone = COUNTRY_ZONES.getOrDefault(request.country().toUpperCase(Locale.ROOT), ZoneId.of("UTC"));
        Instant now = clock.instant();
        LocalDate today = now.atZone(zone).toLocalDate();

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

        // 3. Build recommendations, dropping anything that has already aired, and enrich the
        //    top candidates that still have upcoming airings with review-site ratings.
        List<Recommendation> recommendations = new ArrayList<>();
        int enrichBudget = omdbClient.isEnabled() ? properties.omdb().maxLookups() : 0;
        int enriched = 0;
        for (ShowAggregate aggregate : candidates) {
            List<Airing> upcoming = upcomingAirings(aggregate, zone, today, now);
            if (upcoming.isEmpty()) {
                continue;
            }
            boolean tryEnrich = enriched < enrichBudget && StringUtils.hasText(aggregate.imdbId());
            recommendations.add(toRecommendation(aggregate, upcoming, tryEnrich));
            if (tryEnrich) {
                enriched++;
            }
        }

        // 4. Filter, order by the soonest upcoming airing, and cap to the requested limit.
        String genreFilter = request.genre().map(g -> g.toLowerCase(Locale.ROOT)).orElse(null);
        return recommendations.stream()
                .filter(r -> r.compositeScore() >= request.minRating())
                .filter(r -> matchesGenre(r, genreFilter))
                .sorted(Comparator.comparing(this::soonestStart)
                        .thenComparing(Comparator.comparingDouble(Recommendation::compositeScore).reversed()))
                .limit(request.limit())
                .toList();
    }

    private void accumulate(Map<Long, ShowAggregate> byShow, ScheduleEntry entry) {
        TvMazeShow show = entry.show();
        if (show == null || show.id() == null) {
            return;
        }
        byShow.computeIfAbsent(show.id(), id -> new ShowAggregate(show)).addEntry(entry);
    }

    /** Builds the still-upcoming airings for a show, formatted for scanning and sorted soonest-first. */
    private List<Airing> upcomingAirings(ShowAggregate aggregate, ZoneId zone, LocalDate today, Instant now) {
        TvMazeShow show = aggregate.show();
        String channel = show.network() != null ? show.network().name()
                : (show.webChannel() != null ? show.webChannel().name() : null);

        List<Airing> airings = new ArrayList<>();
        for (ScheduleEntry entry : aggregate.entries()) {
            OffsetDateTime start = parseStart(entry.airstamp());
            // Drop anything that has already started.
            if (start != null && !start.toInstant().isAfter(now)) {
                continue;
            }
            OffsetDateTime local = start == null ? null : start.atZoneSameInstant(zone).toOffsetDateTime();
            airings.add(new Airing(channel, local,
                    local == null ? null : dayLabel(local.toLocalDate(), today),
                    local == null ? null : local.format(TIME),
                    entry.name(), entry.season(), entry.number(), entry.runtime()));
        }
        airings.sort(Comparator.comparing(Airing::start, Comparator.nullsLast(Comparator.naturalOrder())));
        return airings;
    }

    private String dayLabel(LocalDate date, LocalDate today) {
        long delta = today.until(date).getDays();
        if (delta == 0) {
            return "Today";
        }
        if (delta == 1) {
            return "Tomorrow";
        }
        return date.format(DAY);
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

    private Recommendation toRecommendation(ShowAggregate aggregate, List<Airing> airings, boolean enrich) {
        TvMazeShow show = aggregate.show();

        List<RatingSource> sources = new ArrayList<>();
        scoringService.tvmazeRating(show.rating() != null ? show.rating().average() : null)
                .ifPresent(sources::add);
        if (enrich) {
            omdbClient.lookupByImdbId(aggregate.imdbId())
                    .ifPresent(omdb -> sources.addAll(scoringService.omdbRatings(omdb)));
        }

        double composite = scoringService.composite(sources);
        Optional<Airing> next = airings.stream().filter(a -> a.start() != null).findFirst();
        String why = scoringService.buildWhy(show.name(), show.genres(), sources, next);

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

    private OffsetDateTime soonestStart(Recommendation rec) {
        return rec.airings().stream()
                .map(Airing::start)
                .filter(s -> s != null)
                .min(Comparator.naturalOrder())
                .orElse(OffsetDateTime.MAX);
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
        private final List<ScheduleEntry> entries = new ArrayList<>();

        ShowAggregate(TvMazeShow show) {
            this.show = show;
        }

        TvMazeShow show() {
            return show;
        }

        List<ScheduleEntry> entries() {
            return entries;
        }

        void addEntry(ScheduleEntry entry) {
            entries.add(entry);
        }

        double tvmazeScore() {
            return show.rating() != null && show.rating().average() != null ? show.rating().average() : 0.0;
        }

        String imdbId() {
            return show.externals() != null ? show.externals().imdb() : null;
        }
    }
}
