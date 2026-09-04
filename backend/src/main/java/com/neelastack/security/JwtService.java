package com.neelastack.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * @param tokenVersion the token-owning user's current tokenVersion at issuance time —
     *                     embedded as "tv" so a later password reset / MFA disable can
     *                     invalidate this exact token by bumping the DB value past it.
     */
    public String generateAccessToken(UserDetails userDetails, int tokenVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "access");
        claims.put("tv", tokenVersion);
        return buildToken(claims, userDetails.getUsername(), accessTokenExpirationMs, UUID.randomUUID().toString());
    }

    /**
     * Refresh tokens carry a unique jti so a single token can be revoked (logout) without
     * affecting others, and a "fam" (session family) claim shared by every refresh token that
     * descends from the same login via rotation. The family id is what lets AuthService detect
     * refresh-token reuse: if a jti that was already rotated out shows up again, every token in
     * that family gets revoked, not just the one jti.
     */
    public String generateRefreshToken(UserDetails userDetails, String familyId, int tokenVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        claims.put("fam", familyId);
        claims.put("tv", tokenVersion);
        return buildToken(claims, userDetails.getUsername(), refreshTokenExpirationMs, UUID.randomUUID().toString());
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

    private String buildToken(Map<String, Object> claims, String subject, long expirationMs, String jti) {
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .id(jti)
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    /** Null for tokens issued before session-family tracking was added — callers must handle that. */
    public String extractFamilyId(String token) {
        return extractClaim(token, claims -> claims.get("fam", String.class));
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    /** 0 for tokens issued before this claim existed — never matches a real bumped tokenVersion, so those are safely rejected too. */
    public int extractTokenVersion(String token) {
        Integer tv = extractClaim(token, claims -> claims.get("tv", Integer.class));
        return tv != null ? tv : -1;
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * @param currentTokenVersion the user's tokenVersion as read from the DB right now (not
     *                            from the token) — this is what makes password-reset / MFA
     *                            security events retroactively invalidate already-issued tokens.
     */
    public boolean isTokenValid(String token, UserDetails userDetails, int currentTokenVersion) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername())
                && !isTokenExpired(token)
                && extractTokenVersion(token) == currentTokenVersion;
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
