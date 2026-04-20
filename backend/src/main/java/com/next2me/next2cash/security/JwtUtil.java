package com.next2me.next2cash.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${next2cash.jwt.secret}")
    private String jwtSecret;

    @Value("${next2cash.jwt.expiration}")
    private long jwtExpiration; // 8 hours in ms

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateToken(String username, String role, UUID userId,
                                 String allowedSections, List<String> entityIds) {
        var builder = Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("userId", userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration));

        if (allowedSections != null) {
            builder.claim("allowedSections", allowedSections);
        }
        if (entityIds != null && !entityIds.isEmpty()) {
            builder.claim("entityIds", String.join(",", entityIds));
        }

        return builder.signWith(getSigningKey()).compact();
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

    public String getUserIdFromToken(String token) {
        return getClaims(token).get("userId", String.class);
    }

    public String getAllowedSectionsFromToken(String token) {
        return getClaims(token).get("allowedSections", String.class);
    }

    public String getEntityIdsFromToken(String token) {
        return getClaims(token).get("entityIds", String.class);
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
