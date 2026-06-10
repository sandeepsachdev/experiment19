package com.example.freetotv.domain;

/**
 * A single rating from a named source, normalised to a 0-10 scale.
 *
 * @param name          human-readable source name (e.g. "IMDb", "Rotten Tomatoes")
 * @param scoreOutOfTen the rating normalised to 0-10
 * @param display       the rating as originally presented (e.g. "8.4/10", "92%")
 */
public record RatingSource(String name, double scoreOutOfTen, String display) {
}
