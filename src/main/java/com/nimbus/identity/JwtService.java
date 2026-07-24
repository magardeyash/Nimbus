package com.nimbus.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Stateless JWT utility: creates and validates access tokens.
 */
@Component
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** Generate a signed JWT for the given subject (user email). */
    public String generateToken(String subject) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    /** Generate a token that also embeds the user's UUID as a claim. */
    public String generateToken(String subject, UUID userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .claim("uid", userId.toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    /** Extract all claims from a token. Throws if invalid/expired. */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Extract the subject (email) from a token. */
    public String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    /** Validate that the token is well-formed, signed, and not expired. */
    public boolean isTokenValid(String token, String expectedSubject) {
        try {
            String subject = extractSubject(token);
            return subject.equals(expectedSubject) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }
}
