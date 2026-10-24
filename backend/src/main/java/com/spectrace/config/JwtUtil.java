package com.spectrace.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${spectrace.jwt.secret}")
    private String secret;

    @Value("${spectrace.jwt.expire-seconds}")
    private long expireSeconds;

    @PostConstruct
    void checkSecret() {
        // 密钥是唯一防线：缺省即空时必须拒绝启动，否则等于任何人都能伪造管理员 token
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                    "未配置 JWT 签名密钥：请设置环境变量 SPECTRACE_JWT_SECRET（建议 64 位以上随机字符串）后重启");
        }
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claims(Map.of("uid", userId, "role", role))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireSeconds * 1000))
                .signWith(key())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload();
    }
}
