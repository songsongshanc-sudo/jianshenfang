package com.gym.self.modules.user.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class MpJwt {

    private final SecretKey key;
    private final long accessSeconds;
    private final long refreshSeconds;
    private final long faceSeconds;

    public MpJwt(
            @Value("${gym.jwt.mp-secret}") String secret,
            @Value("${gym.jwt.access-minutes}") long accessMinutes,
            @Value("${gym.jwt.refresh-days}") long refreshDays,
            @Value("${gym.jwt.face-minutes}") long faceMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessSeconds = accessMinutes * 60;
        this.refreshSeconds = refreshDays * 24 * 60 * 60;
        this.faceSeconds = faceMinutes * 60;
    }

    public Issued session(long userId) {
        return issue(userId, "wx_session", 30 * 60);
    }

    public Issued face(long userId) {
        return issue(userId, "face", faceSeconds);
    }

    public Issued access(long userId) {
        return issue(userId, "access", accessSeconds);
    }

    public Issued refresh(long userId) {
        return issue(userId, "refresh", refreshSeconds);
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private Issued issue(long userId, String kind, long ttlSeconds) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        String token = Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim("kind", kind)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
        return new Issued(token, jti, kind, ttlSeconds);
    }

    public record Issued(String token, String jti, String kind, long ttlSeconds) {
    }
}
