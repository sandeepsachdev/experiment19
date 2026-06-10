package com.example.freetotv.service;

import java.util.Optional;

/**
 * A normalised request for recommendations.
 *
 * @param country   ISO 3166-1 country code (e.g. "GB", "US")
 * @param days      how many days, starting today, to include
 * @param limit     maximum number of recommendations to return
 * @param minRating minimum composite score (0-10) a title must reach to be included
 * @param genre     optional genre filter (case-insensitive)
 */
public record RecommendationRequest(
        String country,
        int days,
        int limit,
        double minRating,
        Optional<String> genre) {
}
