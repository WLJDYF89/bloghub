package guat.lxy.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtils {

    private static final String SECRET = "bloghub-secret-key-at-least-32-characters-long!!";
    private static final long EXPIRE_TIME = 24 * 60 * 60 * 1000L;
    private static final String BEARER_PREFIX = "Bearer ";

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String userName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("userName", userName);

        return Jwts.builder()
                .claims(claims)
                .subject(userName)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 清洗 token：去除首尾空白 + 去除 "Bearer " 前缀
     * 前端有时候会传 "Bearer eyJhbGci..." 这种带前缀的形式，JJWT 直接解析会报错，所以所有解析前必须先走这里
     */
    public String cleanToken(String token) {
        if (token == null) {
            return null;
        }
        token = token.trim();
        if (token.startsWith(BEARER_PREFIX)) {
            token = token.substring(BEARER_PREFIX.length()).trim();
        }
        return token;
    }

    public Claims parseToken(String token) {
        token = cleanToken(token);
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenExpired(String token) {
        return parseToken(token).getExpiration().before(new Date());
    }

    /**
     * 获取 token 剩余的有效毫秒数（用于 Redis 黑名单的 TTL）
     * - token 还有效：返回剩余毫秒数（正数），Redis 会在这个时间点自动 key 过期，刚好和 JWT 自然过期同步
     * - token 已经过期：返回 0（调用方自己判断 0 就别写 Redis 了，浪费内存）
     */
    public long getRemainingExpirationMillis(String token) {
        Date expiration = parseToken(token).getExpiration();
        long remain = expiration.getTime() - System.currentTimeMillis();
        return Math.max(remain, 0L);
    }
}
