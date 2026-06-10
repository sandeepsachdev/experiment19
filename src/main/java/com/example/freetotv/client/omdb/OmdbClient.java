package com.example.freetotv.client.omdb;

import java.util.Optional;

import com.example.freetotv.client.omdb.dto.OmdbResponse;
import com.example.freetotv.config.AppProperties;
import com.example.freetotv.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Enriches a title with ratings from review sources (IMDb, Rotten Tomatoes, Metacritic) via OMDb.
 *
 * <p>Enrichment is optional: it is skipped entirely unless OMDb is enabled and an API key is
 * configured. Lookups are keyed by IMDb id, which TVmaze supplies for most shows.
 */
@Component
public class OmdbClient {

    private static final Logger log = LoggerFactory.getLogger(OmdbClient.class);

    private final RestClient restClient;
    private final AppProperties properties;

    public OmdbClient(RestClient omdbRestClient, AppProperties properties) {
        this.restClient = omdbRestClient;
        this.properties = properties;
    }

    /** Whether OMDb enrichment is usable (enabled and has an API key). */
    public boolean isEnabled() {
        return properties.omdb().enabled() && StringUtils.hasText(properties.omdb().apiKey());
    }

    @Cacheable(cacheNames = CacheConfig.OMDB_CACHE, key = "#imdbId")
    public Optional<OmdbResponse> lookupByImdbId(String imdbId) {
        if (!isEnabled() || !StringUtils.hasText(imdbId)) {
            return Optional.empty();
        }
        try {
            OmdbResponse body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("i", imdbId)
                            .queryParam("apikey", properties.omdb().apiKey())
                            .build())
                    .retrieve()
                    .body(OmdbResponse.class);
            if (body == null || !body.isSuccess()) {
                return Optional.empty();
            }
            return Optional.of(body);
        } catch (RestClientException ex) {
            log.warn("OMDb lookup failed for {}: {}", imdbId, ex.getMessage());
            return Optional.empty();
        }
    }
}
