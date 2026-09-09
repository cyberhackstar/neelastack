package com.neelastack.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Caches read-heavy public content (services, projects, blog posts) in Redis.
 *
 * Admin write endpoints evict the relevant cache entries via @CacheEvict so the
 * public site does not serve stale content after an edit.
 *
 * The cache is deliberately fail-open: if Redis is unavailable or a cached
 * value cannot be deserialized, the application falls through to the database
 * instead of returning a 500 response.
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig implements CachingConfigurer {

    /**
     * Bump this whenever a cached return type's serialized shape changes in a
     * way that is not safely readable by the new code.
     *
     * The version is included in every cache key prefix. Therefore changing
     * v2 -> v3 makes all previous entries invisible to the new application
     * without requiring FLUSHALL or FLUSHDB.
     *
     * This is important because Redis also contains security/rate-limit state
     * and must not be globally flushed.
     */
    @Value("${app.cache.schema-version:v3}")
    private String cacheSchemaVersion;

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {

        /*
         * Use Spring Data Redis' GenericJackson2JsonRedisSerializer with its
         * built-in type handling rather than manually configuring
         * ObjectMapper.activateDefaultTyping(... NON_FINAL ...).
         *
         * The previous custom NON_FINAL configuration could produce cache
         * entries whose embedded Jackson type information could not be
         * reconstructed correctly when reading generic Object values back
         * from Redis.
         */
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                serializer))
                .computePrefixWith(
                        cacheName -> "neelastack:"
                                + cacheSchemaVersion
                                + ":"
                                + cacheName
                                + "::");

        return builder -> builder.cacheDefaults(defaultConfig);
    }

    /**
     * Redis/cache failures must not make public content endpoints fail.
     *
     * A cache GET/PUT/EVICT failure is logged and the application continues
     * using the database or otherwise completes the original operation.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(
                    RuntimeException exception,
                    Cache cache,
                    Object key) {

                log.warn(
                        "Cache GET failed for cache '{}', key '{}' — "
                                + "falling through to the database: {}",
                        cache.getName(),
                        key,
                        exception.getMessage());
            }

            @Override
            public void handleCachePutError(
                    RuntimeException exception,
                    Cache cache,
                    Object key,
                    Object value) {

                log.warn(
                        "Cache PUT failed for cache '{}', key '{}' — "
                                + "result was not cached: {}",
                        cache.getName(),
                        key,
                        exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(
                    RuntimeException exception,
                    Cache cache,
                    Object key) {

                log.warn(
                        "Cache EVICT failed for cache '{}', key '{}': {}",
                        cache.getName(),
                        key,
                        exception.getMessage());
            }

            @Override
            public void handleCacheClearError(
                    RuntimeException exception,
                    Cache cache) {

                log.warn(
                        "Cache CLEAR failed for cache '{}': {}",
                        cache.getName(),
                        exception.getMessage());
            }
        };
    }
}