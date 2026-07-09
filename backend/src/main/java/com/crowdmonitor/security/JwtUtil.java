package com.crowdmonitor.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    public String generateToken(UserDetails userDetails) {
        return createToken(new HashMap<>(), userDetails.getUsername(), jwtExpiration);
    }

    public String generateToken(UserDetails userDetails, Map<String, Object> extraClaims) {
        return createToken(extraClaims, userDetails.getUsername(), jwtExpiration);
    }

    private String createToken(Map<String, Object> claims, String subject, long expiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * FIX: The original code called Decoders.BASE64.encode(secret.getBytes()).getBytes()
     * which BASE64-ENCODES the secret string and then takes bytes of the encoded string —
     * producing a mangled key that is inconsistent across JVM restarts when the underlying
     * charset differs, and is not a proper HMAC key derivation.
     *
     * Correct approach: use the raw UTF-8 bytes of the secret directly.
     * Keys.hmacShaKeyFor() accepts any byte[] of sufficient length (>= 32 bytes for HS256).
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}
