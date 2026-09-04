package com.neelastack.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Short-lived, single-use tokens backed by Redis: email verification, password
 * reset, and OAuth2 exchange codes. Each token is consumed (deleted) on first
 * successful read via {@link #consume}, so replay is impossible even within
 * the TTL window.
 */
@Service
@RequiredArgsConstructor
public class OneTimeTokenService {

    private final StringRedisTemplate redisTemplate;

    public String issue(String namespace, String value, Duration ttl) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(key(namespace, token), value, ttl);
        return token;
    }

    /**
     * Reads and deletes the token in a single atomic Redis round trip (GETDEL), so a second
     * concurrent call with the same token — two racing browser tabs on the same password-reset
     * or verification link, a client retry racing itself — cannot both observe the value before
     * either delete runs. The previous GET-then-DELETE pair had exactly that window: both
     * requests could read the value before either issued the delete, letting the same one-time
     * token be consumed twice. {@code getAndDelete} maps to Redis's own GETDEL command
     * (available since Redis 6.2; Spring Data Redis exposes it on ValueOperations since 2.6),
     * which the server executes as a single atomic operation.
     */
    public Optional<String> consume(String namespace, String token) {
        String value = redisTemplate.opsForValue().getAndDelete(key(namespace, token));
        return Optional.ofNullable(value);
    }

    /**
     * Reads a token WITHOUT deleting it — for multi-step flows (e.g. login MFA) where a
     * wrong code on one attempt shouldn't burn the whole challenge; pair with {@link #invalidate}
     * once the flow actually completes.
     */
    public Optional<String> read(String namespace, String token) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(namespace, token)));
    }

    /** Explicitly deletes a token issued for a multi-step flow — call once that flow succeeds. */
    public void invalidate(String namespace, String token) {
        redisTemplate.delete(key(namespace, token));
    }

    private String key(String namespace, String token) {
        return namespace + ":" + token;
    }
}
