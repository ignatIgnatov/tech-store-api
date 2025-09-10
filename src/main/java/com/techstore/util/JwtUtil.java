package com.techstore.util;

import com.techstore.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
@Slf4j
public class JwtUtil {

    @Value("${app.jwt.secret:mySecretKey}")
    private String secret;

    @Value("${app.jwt.expiration:86400000}") // 24 hours in milliseconds
    private Long jwtExpiration;

    @Value("${app.jwt.refresh-expiration:604800000}") // 7 days in milliseconds
    private Long refreshExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            log.error("JWT parsing failed: {}", e.getMessage());
            throw new RuntimeException("Invalid JWT token");
        }
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("email", user.getEmail());
        claims.put("userId", user.getId());
        return createToken(claims, user.getUsername(), jwtExpiration);
    }

    public String generateRefreshToken(User user) {
        return createToken(new HashMap<>(), user.getUsername(), refreshExpiration);
    }

    public String generatePasswordResetToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "password-reset");
        return createToken(claims, user.getUsername(), 3600000L); // 1 hour
    }

    public String generateEmailVerificationToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "email-verification");
        return createToken(claims, user.getUsername(), 86400000L); // 24 hours
    }

    private String createToken(Map<String, Object> claims, String subject, Long expiration) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public Boolean isPasswordResetTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return "password-reset".equals(claims.get("type")) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean isEmailVerificationTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return "email-verification".equals(claims.get("type")) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}