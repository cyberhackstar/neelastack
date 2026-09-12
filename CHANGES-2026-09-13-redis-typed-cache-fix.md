# Redis typed-cache fix — 2026-09-13

## Problem

The Redis cache used `GenericJackson2JsonRedisSerializer` for all cache values. For cached collection return values such as `List<ServiceDto>` and `List<ProjectDto>`, a Redis round-trip could reconstruct elements as `LinkedHashMap` instances. The public HTTP response then failed in Jackson with `object is not an instance of declaring class`.

The failure was reproduced locally by clearing Redis: the first `/api/v1/public/services` request returned 200, while the second request returned 500.

## Fix

`backend/src/main/java/com/neelastack/config/CacheConfig.java` now:

- bumps the cache namespace schema from v7 to v8;
- keeps the application-configured Jackson 2 mapper for Java-time support;
- uses explicit `JavaType` + `Jackson2JsonRedisSerializer` configurations for all known cacheable DTO values:
  - `projects` → `List<ProjectDto>`
  - `featuredProjects` → `List<ProjectDto>`
  - `services` → `List<ServiceDto>`
  - `techStackPages` → `List<TechStackPageDto>`
  - `techStackPageBySlug` → `TechStackPageDto`
  - `blogPostBySlug` → `BlogPostDto`
  - `pricingRules` → `Optional<PricingRuleDto>`
- retains the generic serializer as the default fallback for any future cache not explicitly typed.
- retains fail-open cache error handling.

The v8 namespace prevents values written under the previous serializer schema from being read by the new typed configurations.

## Verification status

The uploaded project was modified from the exact ZIP provided in this conversation. A local Maven/Docker build could not be executed in the analysis environment because Maven and Docker are not installed there.

After replacing the Windows working tree with this ZIP, run the project's normal backend build/tests, then verify that repeated requests to `/api/v1/public/projects`, `/api/v1/public/services`, and `/api/v1/public/solutions` remain 200 without clearing Redis between requests.
