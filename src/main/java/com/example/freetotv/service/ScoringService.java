package com.example.freetotv.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.example.freetotv.client.omdb.dto.OmdbResponse;
import com.example.freetotv.config.AppProperties;
import com.example.freetotv.domain.Airing;
import com.example.freetotv.domain.RatingSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Turns raw ratings from the data sources into normalised {@link RatingSource}s, blends them into
 * a single composite score, and produces a human-readable recommendation rationale.
 */
@Service
public class ScoringService {

    public static final String IMDB = "IMDb";
    public static final String ROTTEN_TOMATOES = "Rotten Tomatoes";
    public static final String METACRITIC = "Metacritic";
    public static final String TVMAZE = "TVmaze";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AppProperties properties;

    public ScoringService(AppProperties properties) {
        this.properties = properties;
    }

    /** Builds the TVmaze crowd rating source, if present. */
    public Optional<RatingSource> tvmazeRating(Double average) {
        if (average == null) {
            return Optional.empty();
        }
        return Optional.of(new RatingSource(TVMAZE, clamp(average), format(average) + "/10"));
    }

    /** Extracts IMDb, Rotten Tomatoes and Metacritic ratings from an OMDb response. */
    public List<RatingSource> omdbRatings(OmdbResponse omdb) {
        List<RatingSource> sources = new ArrayList<>();
        if (omdb == null) {
            return sources;
        }

        parseOutOfTen(omdb.imdbRating())
                .ifPresent(v -> sources.add(new RatingSource(IMDB, v, format(v) + "/10")));

        parsePercentFromRatings(omdb, ROTTEN_TOMATOES)
                .ifPresent(v -> sources.add(
                        new RatingSource(ROTTEN_TOMATOES, v, Math.round(v * 10) + "%")));

        parseMetascore(omdb)
                .ifPresent(v -> sources.add(
                        new RatingSource(METACRITIC, v, Math.round(v * 10) + "/100")));

        return sources;
    }

    /**
     * Blends the supplied rating sources into a 0-10 score using the configured weights,
     * re-normalised across whichever sources are present. Returns 0 when there are no ratings.
     */
    public double composite(List<RatingSource> sources) {
        double weightedSum = 0;
        double weightTotal = 0;
        for (RatingSource source : sources) {
            double weight = weightFor(source.name());
            weightedSum += source.scoreOutOfTen() * weight;
            weightTotal += weight;
        }
        if (weightTotal == 0) {
            return 0;
        }
        return Math.round((weightedSum / weightTotal) * 100.0) / 100.0;
    }

    /** Composes a one-line explanation of why a title is recommended. */
    public String buildWhy(String title, List<String> genres, List<RatingSource> sources,
                           Optional<Airing> nextAiring, ZoneId zone) {
        StringBuilder sb = new StringBuilder();
        if (genres != null && !genres.isEmpty()) {
            sb.append("Well-rated ").append(genres.get(0).toLowerCase(Locale.ROOT));
            sb.append(sources.isEmpty() ? " pick" : "");
        } else {
            sb.append("Recommended");
        }

        if (!sources.isEmpty()) {
            String breakdown = sources.stream()
                    .map(s -> s.name() + " " + s.display())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            sb.append(" (").append(breakdown).append(")");
        }

        nextAiring.ifPresent(airing -> sb.append(describeAiring(airing, zone)));
        sb.append('.');
        return sb.toString();
    }

    private String describeAiring(Airing airing, ZoneId zone) {
        if (airing.start() == null) {
            return airing.channel() != null ? " on " + airing.channel() : "";
        }
        OffsetDateTime local = airing.start().atZoneSameInstant(zone).toOffsetDateTime();
        String day = relativeDay(local, zone);
        String channel = StringUtils.hasText(airing.channel()) ? " on " + airing.channel() : "";
        return " — airing " + day + " at " + local.format(TIME) + channel;
    }

    private String relativeDay(OffsetDateTime when, ZoneId zone) {
        var today = OffsetDateTime.now(zone).toLocalDate();
        var date = when.toLocalDate();
        long delta = today.until(date).getDays();
        if (delta == 0) {
            return "today";
        }
        if (delta == 1) {
            return "tomorrow";
        }
        return when.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private double weightFor(String sourceName) {
        AppProperties.Scoring s = properties.scoring();
        return switch (sourceName) {
            case IMDB -> s.imdb();
            case ROTTEN_TOMATOES -> s.rottenTomatoes();
            case METACRITIC -> s.metacritic();
            case TVMAZE -> s.tvmaze();
            default -> 0.0;
        };
    }

    private Optional<Double> parseOutOfTen(String raw) {
        if (!isUsable(raw)) {
            return Optional.empty();
        }
        try {
            return Optional.of(clamp(Double.parseDouble(raw.trim())));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private Optional<Double> parseMetascore(OmdbResponse omdb) {
        if (isUsable(omdb.metascore())) {
            try {
                return Optional.of(clamp(Double.parseDouble(omdb.metascore().trim()) / 10.0));
            } catch (NumberFormatException ignored) {
                // fall through to the ratings array
            }
        }
        return parseRatingValue(omdb, METACRITIC, "/100", 100.0);
    }

    private Optional<Double> parsePercentFromRatings(OmdbResponse omdb, String source) {
        return parseRatingValue(omdb, source, "%", 100.0);
    }

    private Optional<Double> parseRatingValue(OmdbResponse omdb, String source, String suffix, double scale) {
        if (omdb.ratings() == null) {
            return Optional.empty();
        }
        for (OmdbResponse.Rating rating : omdb.ratings()) {
            if (rating.source() != null && rating.source().contains(source) && isUsable(rating.value())) {
                String numeric = rating.value().trim();
                int idx = numeric.indexOf(suffix);
                if (idx > 0) {
                    numeric = numeric.substring(0, idx);
                }
                try {
                    return Optional.of(clamp(Double.parseDouble(numeric.trim()) / scale * 10.0));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private boolean isUsable(String raw) {
        return StringUtils.hasText(raw) && !"N/A".equalsIgnoreCase(raw.trim());
    }

    private double clamp(double value) {
        double bounded = Math.max(0.0, Math.min(10.0, value));
        return Math.round(bounded * 100.0) / 100.0;
    }

    private String format(double value) {
        return String.valueOf(Math.round(value * 10) / 10.0);
    }
}
