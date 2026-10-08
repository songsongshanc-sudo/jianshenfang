package com.gym.self.modules.adminuser.auth;

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
public class AdminJwt {

    private final SecretKey key;
    private final long accessSeconds;
    private final long refreshSeconds;

    public AdminJwt(
            @Value("${gym.jwt.admin-secret}") String secret,
            @Value("${gym.jwt.access-minutes}") long accessMinutes,
            @Value("${gym.jwt.refresh-days}") long refreshDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessSeconds = accessMinutes * 60;
        this.refreshSeconds = refreshDays * 24 * 60 * 60;
    }

    public Issued issue(AdminPrincipal principal, String kind, long ttlSeconds) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        String token = Jwts.builder()
                .id(jti)
                .subject(String.valueOf(principal.id()))
                .claim("role", principal.role())
                .claim("storeId", principal.storeId())
                .claim("kind", kind)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
        return new Issued(token, jti, ttlSeconds);
    }

    public Issued access(AdminPrincipal principal) {
        return issue(principal, "access", accessSeconds);
    }

    public Issued refresh(AdminPrincipal principal) {
        return issue(principal, "refresh", refreshSeconds);
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public record Issued(String token, String jti, long ttlSeconds) {
    }
}
