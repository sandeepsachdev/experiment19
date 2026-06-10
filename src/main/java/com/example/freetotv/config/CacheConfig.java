package com.example.freetotv.config;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caches external API responses so we do not re-fetch the same schedule or rating repeatedly.
 *
 * <p>TV schedules are stable within a day, so a short TTL keeps results "real-time enough"
 * while protecting the upstream free APIs from excessive load.
 */
@Configuration
public class CacheConfig {

    public static final String SCHEDULE_CACHE = "tvmazeSchedule";
    public static final String OMDB_CACHE = "omdbRatings";

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(SCHEDULE_CACHE, OMDB_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(Duration.ofMinutes(30)));
        return manager;
    }
}
