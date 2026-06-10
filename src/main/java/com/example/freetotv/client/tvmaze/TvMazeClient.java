package com.example.freetotv.client.tvmaze;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import com.example.freetotv.client.tvmaze.dto.ScheduleEntry;
import com.example.freetotv.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads the free-to-air / broadcast TV schedule from the public TVmaze API.
 *
 * <p>TVmaze requires no API key. The {@code /schedule} endpoint returns the broadcast
 * (network) airings for a given country and day.
 */
@Component
public class TvMazeClient {

    private static final Logger log = LoggerFactory.getLogger(TvMazeClient.class);
    private static final ParameterizedTypeReference<List<ScheduleEntry>> ENTRY_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public TvMazeClient(RestClient tvMazeRestClient) {
        this.restClient = tvMazeRestClient;
    }

    /**
     * Fetches the broadcast schedule for the given ISO country code and date.
     *
     * @return the airings, or an empty list if the upstream call fails (so one bad day does
     *         not break a multi-day request).
     */
    @Cacheable(cacheNames = CacheConfig.SCHEDULE_CACHE, key = "#countryCode + ':' + #date")
    public List<ScheduleEntry> getSchedule(String countryCode, LocalDate date) {
        try {
            List<ScheduleEntry> entries = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/schedule")
                            .queryParam("country", countryCode)
                            .queryParam("date", date.toString())
                            .build())
                    .retrieve()
                    .body(ENTRY_LIST);
            return entries == null ? List.of() : entries;
        } catch (RestClientException ex) {
            log.warn("TVmaze schedule lookup failed for {} on {}: {}", countryCode, date, ex.getMessage());
            return Collections.emptyList();
        }
    }
}
