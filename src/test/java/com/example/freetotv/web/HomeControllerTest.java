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
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeController.class)
@Import(Countries.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService recommendationService;

    @MockBean
    private RequestMapper requestMapper;

    @Test
    void rendersCompactCardWithExpandableDescription() throws Exception {
        when(requestMapper.toRequest(any(), any(), any(), any(), any()))
                .thenReturn(new RecommendationRequest("AU", 3, 25, 0.0, Optional.empty()));

        Recommendation rec = new Recommendation(
                42L, "Brilliant Drama", "Scripted", List.of("Drama"),
                "A gripping tale of suspense.", "http://img/o.jpg", "http://site", 8.6,
                List.of(new RatingSource("IMDb", 8.4, "8.4/10")),
                List.of(new Airing("ABC", null, "Today", "21:00", "The Pilot", 1, 1, 60)),
                "Well-rated drama.");
        when(recommendationService.recommend(any())).thenReturn(List.of(rec));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                // Country dropdown defaults to Australia.
                .andExpect(content().string(containsString("selected=\"selected\">Australia")))
                // Compact card: thumbnail, scannable next-up line, and an expandable "More" section.
                .andExpect(content().string(containsString("has-thumb")))
                .andExpect(content().string(containsString("class=\"thumb\"")))
                .andExpect(content().string(containsString("class=\"next-up\"")))
                .andExpect(content().string(containsString("<summary>More</summary>")))
                .andExpect(content().string(containsString("A gripping tale of suspense.")))
                .andExpect(content().string(containsString("Brilliant Drama")));
    }
}
