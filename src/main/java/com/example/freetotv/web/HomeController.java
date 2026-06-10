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

    /** Common TVmaze genres offered in the genre dropdown. */
    static final List<String> GENRES = List.of(
            "Action", "Adventure", "Anime", "Children", "Comedy", "Crime", "Documentary", "Drama",
            "Family", "Fantasy", "Food", "History", "Horror", "Music", "Mystery", "Nature",
            "Romance", "Science-Fiction", "Sports", "Thriller", "Travel", "War", "Western");

    private final RecommendationService recommendationService;
    private final RequestMapper requestMapper;
    private final Countries countries;

    public HomeController(RecommendationService recommendationService, RequestMapper requestMapper,
                          Countries countries) {
        this.recommendationService = recommendationService;
        this.requestMapper = requestMapper;
        this.countries = countries;
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
        model.addAttribute("countries", countries.all());
        model.addAttribute("genres", GENRES);
        model.addAttribute("country", request.country());
        model.addAttribute("countryName", countries.nameFor(request.country()));
        model.addAttribute("days", request.days());
        model.addAttribute("genre", request.genre().orElse(""));
        model.addAttribute("minRating", request.minRating());
        return "index";
    }
}
