package com.example.freetotv.web;

import java.util.List;

import com.example.freetotv.domain.Recommendation;
import com.example.freetotv.service.RecommendationRequest;
import com.example.freetotv.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON API for free-to-air TV recommendations.
 *
 * <p>Example: {@code GET /api/recommendations?country=GB&days=3&limit=20&genre=Drama&minRating=7}
 */
@RestController
@RequestMapping("/api")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final RequestMapper requestMapper;

    public RecommendationController(RecommendationService recommendationService, RequestMapper requestMapper) {
        this.recommendationService = recommendationService;
        this.requestMapper = requestMapper;
    }

    @GetMapping("/recommendations")
    public List<Recommendation> recommendations(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) String genre) {
        RecommendationRequest request = requestMapper.toRequest(country, days, limit, minRating, genre);
        return recommendationService.recommend(request);
    }
}
