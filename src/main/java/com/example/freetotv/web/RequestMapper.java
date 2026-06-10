package com.example.freetotv.web;

import java.util.Optional;

import com.example.freetotv.config.AppProperties;
import com.example.freetotv.service.RecommendationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds a validated {@link RecommendationRequest} from raw, optional query parameters,
 * applying configured defaults and clamping values to safe ranges.
 */
@Component
public class RequestMapper {

    static final int MAX_DAYS = 7;
    static final int MAX_LIMIT = 100;

    private final AppProperties properties;

    public RequestMapper(AppProperties properties) {
        this.properties = properties;
    }

    public RecommendationRequest toRequest(String country, Integer days, Integer limit,
                                           Double minRating, String genre) {
        AppProperties.Defaults defaults = properties.defaults();
        String resolvedCountry = StringUtils.hasText(country)
                ? country.trim().toUpperCase(java.util.Locale.ROOT)
                : defaults.country();
        int resolvedDays = clamp(days != null ? days : defaults.days(), 1, MAX_DAYS);
        int resolvedLimit = clamp(limit != null ? limit : defaults.limit(), 1, MAX_LIMIT);
        double resolvedMin = clampDouble(minRating != null ? minRating : defaults.minRating(), 0.0, 10.0);
        Optional<String> resolvedGenre = StringUtils.hasText(genre)
                ? Optional.of(genre.trim())
                : Optional.empty();
        return new RecommendationRequest(resolvedCountry, resolvedDays, resolvedLimit, resolvedMin, resolvedGenre);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
