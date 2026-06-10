package com.example.freetotv.web;

import java.util.List;
import java.util.Optional;

import com.example.freetotv.domain.Airing;
import com.example.freetotv.domain.RatingSource;
import com.example.freetotv.domain.Recommendation;
import com.example.freetotv.service.RecommendationRequest;
import com.example.freetotv.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService recommendationService;

    @MockBean
    private RequestMapper requestMapper;

    @Test
    void returnsRecommendationsAsJson() throws Exception {
        RecommendationRequest request = new RecommendationRequest("GB", 3, 25, 0.0, Optional.empty());
        when(requestMapper.toRequest(any(), any(), any(), any(), any())).thenReturn(request);

        Recommendation rec = new Recommendation(
                42L, "Brilliant Drama", "Scripted", List.of("Drama"),
                "A gripping tale.", "http://img/o.jpg", "http://site", 8.6,
                List.of(new RatingSource("IMDb", 8.4, "8.4/10")),
                List.of(new Airing("ABC", null, "Today", "21:00", "The Pilot", 1, 1, 60)),
                "Well-rated drama.");
        when(recommendationService.recommend(any())).thenReturn(List.of(rec));

        mockMvc.perform(get("/api/recommendations").param("country", "GB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Brilliant Drama"))
                .andExpect(jsonPath("$[0].compositeScore").value(8.6))
                .andExpect(jsonPath("$[0].ratingSources[0].name").value("IMDb"))
                .andExpect(jsonPath("$[0].airings[0].channel").value("ABC"))
                .andExpect(jsonPath("$[0].airings[0].dayLabel").value("Today"))
                .andExpect(jsonPath("$[0].airings[0].time").value("21:00"));
    }
}
