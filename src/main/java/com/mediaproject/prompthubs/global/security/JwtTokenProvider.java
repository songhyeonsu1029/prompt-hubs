package com.mediaproject.prompthubs.global.security;

import com.mediaproject.prompthubs.global.config.JwtConfig;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    private final JwtConfig jwtConfig;
    private final RedisTemplate<String, String> redisTemplate;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UUID accountId, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getAccessExpiration());

        return Jwts.builder()
                .subject(accountId.toString())
                .claim("email", email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String createRefreshToken(UUID accountId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getRefreshExpiration());

        String refreshToken = Jwts.builder()
                .subject(accountId.toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();

        // Store refresh token in Redis
        String key = REFRESH_TOKEN_PREFIX + accountId.toString();
        redisTemplate.opsForValue().set(
                key,
                refreshToken,
                jwtConfig.getRefreshExpiration(),
                TimeUnit.MILLISECONDS
        );

        return refreshToken;
    }

    public UUID getAccountIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    public String getEmailFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("email", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token");
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    public boolean validateRefreshToken(String refreshToken, UUID accountId) {
        String key = REFRESH_TOKEN_PREFIX + accountId.toString();
        String storedToken = redisTemplate.opsForValue().get(key);

        if (storedToken == null || !storedToken.equals(refreshToken)) {
            return false;
        }

        return validateToken(refreshToken);
    }

    public void deleteRefreshToken(UUID accountId) {
        String key = REFRESH_TOKEN_PREFIX + accountId.toString();
        redisTemplate.delete(key);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}