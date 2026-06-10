package com.example.freetotv.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient} instances used to talk to the external data sources.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class RestClientConfig {

    private ClientHttpRequestFactory requestFactory() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(10));
        return ClientHttpRequestFactories.get(settings);
    }

    @Bean
    RestClient tvMazeRestClient(AppProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.tvmaze().baseUrl())
                .requestFactory(requestFactory())
                .defaultHeader("User-Agent", "freetotv-recommender/0.1 (+https://github.com)")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean
    RestClient omdbRestClient(AppProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.omdb().baseUrl())
                .requestFactory(requestFactory())
                .defaultHeader("User-Agent", "freetotv-recommender/0.1 (+https://github.com)")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
