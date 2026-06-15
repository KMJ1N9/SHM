package com.shm.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 双 Token 工具类（与 Node.js auth.js 行为完全一致）
 *
 * <h3>Token 结构</h3>
 * <ul>
 *   <li>Access Token — 7 天有效，payload: { sub, role, tv }</li>
 *   <li>Refresh Token — 30 天有效，payload: { sub, tv, type: "refresh" }</li>
 * </ul>
 *
 * <h3>用法</h3>
 * 在 core-service 的 @Configuration 中创建为 @Bean：
 * <pre>{@code
 * @Bean
 * public JwtUtil jwtUtil(@Value("${jwt.access-secret}") String accessSecret,
 *                         @Value("${jwt.refresh-secret}") String refreshSecret) {
 *     return new JwtUtil(accessSecret, refreshSecret);
 * }
 * }</pre>
 */
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** Access Token 有效期：7 天 */
    private static final long ACCESS_EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000L;
    /** Refresh Token 有效期：30 天 */
    private static final long REFRESH_EXPIRATION_MS = 30L * 24 * 60 * 60 * 1000;

    private final SecretKey accessKey;
    private final SecretKey refreshKey;

    /**
     * @param accessSecret  Access Token 签名密钥（HMAC-SHA256）
     * @param refreshSecret Refresh Token 签名密钥（HMAC-SHA256）
     */
    public JwtUtil(String accessSecret, String refreshSecret) {
        this.accessKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
    }

    // ============================================================
    // 生成 Token
    // ============================================================

    /**
     * 签发 Access Token（与 Node.js generateAccessToken 一致）
     *
     * @param userId       用户 ID
     * @param role         用户角色（user / cs / admin）
     * @param tokenVersion Token 版本号
     * @return JWT 字符串
     */
    public String generateAccessToken(Long userId, String role, int tokenVersion) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("tv", tokenVersion)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ACCESS_EXPIRATION_MS))
                .signWith(accessKey)
                .compact();
    }

    /**
     * 签发 Refresh Token（与 Node.js generateRefreshToken 一致）
     *
     * @param userId       用户 ID
     * @param tokenVersion Token 版本号
     * @return JWT 字符串
     */
    public String generateRefreshToken(Long userId, int tokenVersion) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("tv", tokenVersion)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + REFRESH_EXPIRATION_MS))
                .signWith(refreshKey)
                .compact();
    }

    // ============================================================
    // 验证 Token
    // ============================================================

    /**
     * 验证 Access Token 并解析载荷
     *
     * @param token JWT 字符串
     * @return 解析后的载荷
     * @throws ExpiredJwtException Token 已过期
     * @throws io.jsonwebtoken.JwtException Token 无效（签名错误/格式错误等）
     */
    public JwtPayload validateAccessToken(String token) {
        Claims claims = parseToken(token, accessKey);
        return new JwtPayload(
                Long.valueOf(claims.getSubject()),
                claims.get("role", String.class),
                claims.get("tv", Integer.class),
                null
        );
    }

    /**
     * 验证 Refresh Token 并解析载荷
     *
     * @param token JWT 字符串
     * @return 解析后的载荷（type 为 "refresh"）
     * @throws ExpiredJwtException Token 已过期
     * @throws io.jsonwebtoken.JwtException Token 无效
     */
    public JwtPayload validateRefreshToken(String token) {
        Claims claims = parseToken(token, refreshKey);
        return new JwtPayload(
                Long.valueOf(claims.getSubject()),
                null,
                claims.get("tv", Integer.class),
                claims.get("type", String.class)
        );
    }

    /**
     * 快速判断是否为 Refresh Token（不抛异常）
     *
     * @param token JWT 字符串
     * @return true 表示 payload.type == "refresh"
     */
    public boolean isRefreshToken(String token) {
        try {
            JwtPayload payload = validateRefreshToken(token);
            return payload.isRefreshToken();
        } catch (Exception e) {
            log.trace("isRefreshToken 检查失败（非异常，调用方会按 access token 处理）: {}", e.getMessage());
            return false;
        }
    }

    // ============================================================
    // 内部方法
    // ============================================================

    /** 解析 JWT 并返回 Claims */
    private Claims parseToken(String token, SecretKey key) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }
}
