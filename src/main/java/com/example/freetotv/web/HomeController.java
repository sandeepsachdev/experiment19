package com.example.freetotv.web;

import java.util.List;

import com.example.freetotv.domain.Recommendation;
import com.example.freetotv.service.RecommendationRequest;
import com.example.freetotv.service.RecommendationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves the human-facing recommendations page.
 */
@Controller
public class HomeController {

    private final RecommendationService recommendationService;
    private final RequestMapper requestMapper;

    public HomeController(RecommendationService recommendationService, RequestMapper requestMapper) {
        this.recommendationService = recommendationService;
        this.requestMapper = requestMapper;
    }

    @GetMapping("/")
    public String home(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Double minRating,
            Model model) {
        RecommendationRequest request = requestMapper.toRequest(country, days, null, minRating, genre);
        List<Recommendation> recommendations = recommendationService.recommend(request);

        model.addAttribute("recommendations", recommendations);
        model.addAttribute("country", request.country());
        model.addAttribute("days", request.days());
        model.addAttribute("genre", request.genre().orElse(""));
        model.addAttribute("minRating", request.minRating());
        return "index";
    }
}
