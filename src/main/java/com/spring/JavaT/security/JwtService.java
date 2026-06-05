package com.spring.JavaT.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Handles all JWT operations: generation, validation, and claim extraction.
 *
 * <p>Token structure:
 * <ul>
 *   <li>{@code sub}        — username (principal identifier)</li>
 *   <li>{@code iss}        — issuer from {@link JwtProperties}</li>
 *   <li>{@code iat}        — issued-at timestamp</li>
 *   <li>{@code exp}        — expiry timestamp</li>
 *   <li>{@code jti}        — unique token identifier (for revocation)</li>
 *   <li>{@code tokenType}  — {@link TokenType#ACCESS} or {@link TokenType#REFRESH}</li>
 *   <li>{@code role}       — user's role (access tokens only, custom claim)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    static final String JTI_CLAIM        = "jti";
    static final String TOKEN_TYPE_CLAIM = "tokenType";

    private final JwtProperties jwtProperties;

    // -------------------------------------------------------------------------
    // Token generation
    // -------------------------------------------------------------------------

    /**
     * Generates an access token for the given user with no extra claims.
     */
    public String generateAccessToken(UserDetails userDetails) {
        return generateAccessToken(new HashMap<>(), userDetails);
    }

    /**
     * Generates an access token with additional custom claims merged in.
     *
     * @param extraClaims additional claims to embed (e.g. {@code role})
     * @param userDetails the authenticated principal
     */
    public String generateAccessToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put(JTI_CLAIM, UUID.randomUUID().toString());
        claims.put(TOKEN_TYPE_CLAIM, TokenType.ACCESS.name());
        return buildToken(claims, userDetails, jwtProperties.getExpirationMs());
    }

    /**
     * Generates a refresh token. Refresh tokens carry no extra claims beyond
     * the standard ones — they are only used to issue new access tokens.
     */
    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JTI_CLAIM, UUID.randomUUID().toString());
        claims.put(TOKEN_TYPE_CLAIM, TokenType.REFRESH.name());
        return buildToken(claims, userDetails, jwtProperties.getRefreshExpirationMs());
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expirationMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuer(jwtProperties.getIssuer())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the token is structurally valid, not expired,
     * and the subject matches the given {@link UserDetails}.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String subject = extractUsername(token);
            return subject.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Returns {@code true} if the token's expiry timestamp is in the past.
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Returns {@code true} if the token carries the {@link TokenType#REFRESH} claim.
     */
    public boolean isRefreshToken(String token) {
        return TokenType.REFRESH.name().equals(extractTokenType(token));
    }

    // -------------------------------------------------------------------------
    // Claim extraction
    // -------------------------------------------------------------------------

    /** Extracts the {@code sub} (username) claim. */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** Extracts the {@code exp} (expiration) claim. */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /** Extracts the unique {@code jti} claim. */
    public String extractJti(String token) {
        return extractClaim(token, claims -> claims.get(JTI_CLAIM, String.class));
    }

    /** Extracts the {@code tokenType} claim ({@code ACCESS} or {@code REFRESH}). */
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    /**
     * Generic claim extractor. Applies the given resolver function to the
     * parsed {@link Claims} object.
     *
     * @param token    the JWT string
     * @param resolver a function that maps {@link Claims} to the desired value
     * @param <T>      the return type
     */
    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // -------------------------------------------------------------------------
    // Key
    // -------------------------------------------------------------------------

    /**
     * Decodes the Base64 secret from properties and builds an HMAC-SHA key.
     * Called on every token operation — the key object is lightweight.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
