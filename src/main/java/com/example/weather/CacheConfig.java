package com.example.weather;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    CacheManager cacheManager(@Value("${weather.cache.geocode-ttl}") Duration geocodeTtl,
                              @Value("${weather.cache.weather-ttl}") Duration weatherTtl,
                              @Value("${weather.cache.max-entries}") long maxEntries) {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                build("geocode", geocodeTtl, maxEntries),
                build("current", weatherTtl, maxEntries),
                build("forecast", weatherTtl, maxEntries)));
        return manager;
    }

    private static CaffeineCache build(String name, Duration ttl, long maxEntries) {
        return new CaffeineCache(name,
                Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxEntries).recordStats().build());
    }
}
