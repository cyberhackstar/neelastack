package com.neelastack.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.dto.content.BlogPostDto;
import com.neelastack.dto.content.ProjectDto;
import com.neelastack.dto.content.ServiceDto;
import com.neelastack.dto.content.TechStackPageDto;
import com.neelastack.dto.pricing.PricingRuleDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Caches read-heavy public content (services, projects, blog posts) in Redis.
 *
 * The cache is deliberately fail-open: Redis failures never turn a public content
 * request into a 500; the application falls through to the database instead.
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig implements CachingConfigurer {

    /**
     * Cache format version. Bump this when the serialized shape/serializer changes.
     * v8 uses the application's configured Jackson mapper and explicit Java types for
     * all known cacheable DTOs. This prevents collection values from coming back as
     * LinkedHashMap instances after a Redis round-trip.
     */
    @Value("${app.cache.schema-version:v8}")
    private String cacheSchemaVersion;

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(
            ObjectMapper objectMapper) {

        /*
         * Reuse a copy of Spring Boot's configured ObjectMapper rather than creating
         * an isolated default mapper. The application mapper already has the Jackson
         * modules required for Java-time types; findAndRegisterModules() is retained as
         * a defensive measure for environments where module discovery differs.
         */
        ObjectMapper redisObjectMapper = objectMapper.copy();
        redisObjectMapper.findAndRegisterModules();

        GenericJackson2JsonRedisSerializer serializer =
                GenericJackson2JsonRedisSerializer.builder()
                        .objectMapper(redisObjectMapper)
                        .build();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .computePrefixWith(cacheName ->
                        "neelastack:" + cacheSchemaVersion + ":" + cacheName + "::");

        JavaType projectListType = redisObjectMapper.getTypeFactory()
                .constructCollectionType(List.class, ProjectDto.class);
        JavaType serviceListType = redisObjectMapper.getTypeFactory()
                .constructCollectionType(List.class, ServiceDto.class);
        JavaType techStackPageListType = redisObjectMapper.getTypeFactory()
                .constructCollectionType(List.class, TechStackPageDto.class);
        JavaType pricingRuleOptionalType = redisObjectMapper.getTypeFactory()
                .constructParametricType(Optional.class, PricingRuleDto.class);

        RedisCacheConfiguration projectsConfig = typedConfig(
                defaultConfig, new Jackson2JsonRedisSerializer<>(redisObjectMapper, projectListType));
        RedisCacheConfiguration featuredProjectsConfig = typedConfig(
                defaultConfig, new Jackson2JsonRedisSerializer<>(redisObjectMapper, projectListType));
        RedisCacheConfiguration servicesConfig = typedConfig(
                defaultConfig, new Jackson2JsonRedisSerializer<>(redisObjectMapper, serviceListType));
        RedisCacheConfiguration techStackPagesConfig = typedConfig(
                defaultConfig, new Jackson2JsonRedisSerializer<>(redisObjectMapper, techStackPageListType));
        RedisCacheConfiguration techStackPageBySlugConfig = typedConfig(
                defaultConfig, new Jackson2JsonRedisSerializer<>(redisObjectMapper, TechStackPageDto.class));
        RedisCacheConfiguration blogPostBySlugConfig = typedConfig(
                defaultConfig, new Jackson2JsonRedisSerializer<>(redisObjectMapper, BlogPostDto.class));
        RedisCacheConfiguration pricingRulesConfig = typedConfig(
                defaultConfig, new Jackson2JsonRedisSerializer<>(redisObjectMapper, pricingRuleOptionalType));

        return builder -> builder
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("projects", projectsConfig)
                .withCacheConfiguration("featuredProjects", featuredProjectsConfig)
                .withCacheConfiguration("services", servicesConfig)
                .withCacheConfiguration("techStackPages", techStackPagesConfig)
                .withCacheConfiguration("techStackPageBySlug", techStackPageBySlugConfig)
                .withCacheConfiguration("blogPostBySlug", blogPostBySlugConfig)
                .withCacheConfiguration("pricingRules", pricingRulesConfig);
    }

    private RedisCacheConfiguration typedConfig(
            RedisCacheConfiguration baseConfig,
            Jackson2JsonRedisSerializer<?> serializer) {
        return baseConfig.serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }

    /** Fail-open cache error handling. */
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
                log.warn("Cache EVICT failed for cache '{}', key '{}': {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR failed for cache '{}': {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
