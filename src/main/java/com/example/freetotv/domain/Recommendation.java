package com.example.freetotv.domain;

import java.util.List;

/**
 * A recommended title together with everywhere it airs in the requested window, the rating
 * sources behind it, the composite score, and a plain-language explanation.
 *
 * @param showId        TVmaze show id
 * @param title         the show title
 * @param type          show type (e.g. "Scripted", "Documentary")
 * @param genres        genres for the show
 * @param summary       plain-text summary (HTML stripped)
 * @param imageUrl      poster/thumbnail URL, if available
 * @param officialSite  the show's official site, if available
 * @param compositeScore the blended 0-10 score across available sources
 * @param ratingSources the individual ratings that fed the composite score
 * @param airings       every broadcast of this title within the requested window, soonest first
 * @param why           a human-readable reason this title is recommended
 */
public record Recommendation(
        Long showId,
        String title,
        String type,
        List<String> genres,
        String summary,
        String imageUrl,
        String officialSite,
        double compositeScore,
        List<RatingSource> ratingSources,
        List<Airing> airings,
        String why) {
}
