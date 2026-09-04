package com.neelastack.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;

/**
 * Tracks revoked refresh-token jtis, and revoked session families, in Redis (keys expire
 * naturally when the token itself would have expired, so neither list grows unbounded).
 * Access tokens are short-lived (15 min) and intentionally not revocable — revoking the
 * refresh token is enough to end a session within that window.
 *
 * Fail-closed on Redis unavailability: unlike the rate limiter (which fails open to protect
 * request-handling capacity), a revocation check that can't reach Redis treats every token as
 * revoked. The alternative — fail open — would mean a Redis outage silently disables refresh-
 * token revocation and session-family reuse detection, i.e. exactly the moment a stolen token
 * could no longer be shut off. The cost is availability: during a Redis outage, refreshes and
 * family checks fail and users have to log in again once their access token expires. That's an
 * intentional trade for a security-sensitive check — this should be monitored/alerted on so a
 * Redis outage is visibly disrupting logins, not silently weakening security.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRevocationService {

    private final StringRedisTemplate redisTemplate;

    private static final String JTI_PREFIX = "revoked_jti:";
    private static final String FAMILY_PREFIX = "revoked_family:";

    /**
     * Atomically claims a jti for single use, via Redis SET ... NX (set-if-not-exists),
     * which Postgres-style "check then write" cannot replicate: SET NX is one round trip
     * and one atomic operation on the Redis server, so of two concurrent callers racing on
     * the same jti, exactly one gets true and the other gets false — there is no window
     * where both can observe "not yet claimed."
     *
     * @return true if this call is the one that claimed the jti (proceed with rotation);
     *         false if it was already claimed (this is a reuse/replay, or a losing racer).
     */
    public boolean tryClaim(String jti, Date tokenExpiry) {
        long ttlMillis = tokenExpiry.getTime() - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            return false; // already expired -- nothing to claim, caller should treat as invalid
        }
        try {
            Boolean claimed = redisTemplate.opsForValue()
                    .setIfAbsent(JTI_PREFIX + jti, "1", Duration.ofMillis(ttlMillis));
            return Boolean.TRUE.equals(claimed);
        } catch (Exception e) {
            // Same fail-closed posture as isRevoked/isFamilyRevoked below: if Redis is
            // unreachable we cannot safely claim, so treat this as unclaimed/invalid rather
            // than letting the refresh proceed unchecked.
            log.error("Redis unreachable while claiming refresh token jti — failing closed: {}", e.getMessage());
            return false;
        }
    }

    public void revoke(String jti, Date tokenExpiry) {
        long ttlMillis = tokenExpiry.getTime() - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            return; // already expired, nothing to do
        }
        redisTemplate.opsForValue().set(JTI_PREFIX + jti, "1", Duration.ofMillis(ttlMillis));
    }

    public boolean isRevoked(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(JTI_PREFIX + jti));
        } catch (Exception e) {
            log.error("Redis unreachable while checking token revocation — failing closed (treating as revoked): {}", e.getMessage());
            return true;
        }
    }

    /** Revokes every refresh token descended from this session family — used when reuse of an already-rotated token is detected. */
    public void revokeFamily(String familyId, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(FAMILY_PREFIX + familyId, "1", ttl);
    }

    public boolean isFamilyRevoked(String familyId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(FAMILY_PREFIX + familyId));
        } catch (Exception e) {
            log.error("Redis unreachable while checking session-family revocation — failing closed (treating as revoked): {}", e.getMessage());
            return true;
        }
    }
}
