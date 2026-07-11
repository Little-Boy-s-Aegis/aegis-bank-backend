package com.example.bank.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import java.io.Serializable;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtTokenUtil implements Serializable {
    private static final long serialVersionUID = -2550185165626007488L;

    @Autowired
    private com.example.bank.repository.BlacklistedTokenRepository blacklistedTokenRepository;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private Key getSigningKey() {
        byte[] keyBytes = secret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    private Boolean isTokenExpired(String token) {
        final Date expirationDate = getExpirationDateFromToken(token);
        return expirationDate.before(new Date());
    }

    private final java.util.concurrent.ConcurrentMap<String, java.util.Set<String>> issuedTokensByUser = new java.util.concurrent.ConcurrentHashMap<>();

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("jti", java.util.UUID.randomUUID().toString());
        String token = doGenerateToken(claims, userDetails.getUsername());
        issuedTokensByUser
                .computeIfAbsent(userDetails.getUsername(), key -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                .add(token);
        return token;
    }

    private String doGenerateToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public void blacklistToken(String token) {
        if (token != null) {
            java.time.LocalDateTime expiry = java.time.LocalDateTime.now().plusHours(24);
            try {
                Date expDate = getExpirationDateFromToken(token);
                if (expDate != null) {
                    expiry = expDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
                }
            } catch (Exception ignored) {}

            try {
                com.example.bank.model.BlacklistedToken blToken = new com.example.bank.model.BlacklistedToken(token, expiry);
                blacklistedTokenRepository.save(blToken);
            } catch (Exception e) {
                // Ignore unique constraint violations if token is already blacklisted
            }

            try {
                String username = getUsernameFromToken(token);
                java.util.Set<String> userTokens = issuedTokensByUser.get(username);
                if (userTokens != null) {
                    userTokens.remove(token);
                }
            } catch (Exception ignored) {}
        }
    }

    public boolean isTokenBlacklisted(String token) {
        if (token == null) {
            return false;
        }
        try {
            return blacklistedTokenRepository.findByToken(token).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    public void blacklistAllTokensForUser(String username) {
        if (username == null) {
            return;
        }
        java.util.Set<String> userTokens = issuedTokensByUser.remove(username);
        if (userTokens != null) {
            for (String token : userTokens) {
                blacklistToken(token);
            }
        }
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token) && !isTokenBlacklisted(token));
    }
}
