package com.neelastack.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Duration;

/**
 * Caches read-heavy public content (services, projects, blog posts) in Redis.
 * Admin write endpoints evict the relevant cache entries (@CacheEvict) so the
 * public site never serves stale content after an edit.
 *
 * Implements CachingConfigurer (rather than just exposing a stray
 * 
 * @Bean CacheErrorHandler) because that's the only mechanism Spring's cache
 *       AOP interceptor actually consults for a custom error handler — a bare
 * @Bean of type CacheErrorHandler sitting in the context is silently
 *       ignored, which is a mistake worth flagging since it's an easy one to
 *       make.
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        // activateDefaultTyping lives on ObjectMapper, not on
        // Jackson2ObjectMapperBuilder
        // (the builder has no such method) — build the mapper first, then call it on
        // the built instance, which mutates it in place and returns `this` for
        // chaining.
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        return builder -> builder.cacheDefaults(defaultConfig);
    }

    /**
     * Without this, Spring's default error handler rethrows any Redis
     * failure (connection refused, timeout, serialization error — including
     * a stale/incompatible cache entry left over from a previous deploy)
     * straight through the annotated method, meaning every @Cacheable public
     * endpoint starts returning 500s instead of just serving uncached data.
     * This mirrors the fail-open policy already used in RateLimitFilter.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET failed for cache '{}', key '{}' — falling through to the database: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed for cache '{}', key '{}' — result was not cached: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache EVICT failed for cache '{}', key '{}': {}", cache.getName(), key,
                        exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR failed for cache '{}': {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
