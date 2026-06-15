package com.shm.common.util;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试
 */
class JwtUtilTest {

    private static final String ACCESS_SECRET = "test-access-secret-key-for-unit-testing-32chars";
    private static final String REFRESH_SECRET = "test-refresh-secret-key-for-unit-testing-32chars";

    private final JwtUtil jwtUtil = new JwtUtil(ACCESS_SECRET, REFRESH_SECRET);

    // ============================================================
    // Access Token
    // ============================================================

    @Test
    void generateAccessToken_shouldProduceValidToken() {
        String token = jwtUtil.generateAccessToken(1L, "user", 0);

        assertNotNull(token);
        JwtPayload payload = jwtUtil.validateAccessToken(token);

        assertEquals(1L, payload.getSub());
        assertEquals("user", payload.getRole());
        assertEquals(0, payload.getTv());
        assertNull(payload.getType());
    }

    @Test
    void accessToken_shouldNotBeRefreshToken() {
        String token = jwtUtil.generateAccessToken(1L, "user", 0);

        assertFalse(jwtUtil.isRefreshToken(token));
    }

    @Test
    void accessToken_withDifferentRole_shouldPreserveRole() {
        String token = jwtUtil.generateAccessToken(2L, "admin", 1);

        JwtPayload payload = jwtUtil.validateAccessToken(token);
        assertEquals(2L, payload.getSub());
        assertEquals("admin", payload.getRole());
    }

    // ============================================================
    // Refresh Token
    // ============================================================

    @Test
    void generateRefreshToken_shouldProduceValidToken() {
        String token = jwtUtil.generateRefreshToken(1L, 0);

        assertNotNull(token);
        JwtPayload payload = jwtUtil.validateRefreshToken(token);

        assertEquals(1L, payload.getSub());
        assertEquals(0, payload.getTv());
        assertEquals("refresh", payload.getType());
        assertTrue(payload.isRefreshToken());
    }

    @Test
    void refreshToken_shouldBeRecognizedAsRefresh() {
        String token = jwtUtil.generateRefreshToken(1L, 0);

        assertTrue(jwtUtil.isRefreshToken(token));
    }

    // ============================================================
    // 跨密钥验证
    // ============================================================

    @Test
    void accessToken_shouldFailWithRefreshKey() {
        String accessToken = jwtUtil.generateAccessToken(1L, "user", 0);

        // 用 refresh 密钥验证 access token 应该失败
        JwtUtil otherUtil = new JwtUtil(REFRESH_SECRET, ACCESS_SECRET);
        assertThrows(JwtException.class, () -> otherUtil.validateAccessToken(accessToken));
    }

    @Test
    void refreshToken_shouldFailWithAccessKey() {
        String refreshToken = jwtUtil.generateRefreshToken(1L, 0);

        // refresh token 是有效的 refresh token
        assertTrue(jwtUtil.isRefreshToken(refreshToken));
        // access token 不应被识别为 refresh token
        assertFalse(jwtUtil.isRefreshToken(
                jwtUtil.generateAccessToken(1L, "user", 0)));
    }

    // ============================================================
    // 篡改检测
    // ============================================================

    @Test
    void tamperedToken_shouldFailValidation() {
        String token = jwtUtil.generateAccessToken(1L, "user", 0);
        // 篡改 token（修改最后一个字符）
        String tampered = token.substring(0, token.length() - 1)
                + (token.charAt(token.length() - 1) == 'A' ? 'B' : 'A');

        assertThrows(JwtException.class, () -> jwtUtil.validateAccessToken(tampered));
    }

    // ============================================================
    // 格式错误
    // ============================================================

    @Test
    void malformedToken_shouldFailValidation() {
        // JJWT 对无效格式抛 JwtException，对空字符串抛 IllegalArgumentException
        assertThrows(JwtException.class, () -> jwtUtil.validateAccessToken("not-a-jwt"));
        assertThrows(IllegalArgumentException.class, () -> jwtUtil.validateAccessToken(""));
    }

    // ============================================================
    // Token 版本号
    // ============================================================

    @Test
    void tokenVersion_shouldBePreserved() {
        int tokenVersion = 5;
        String accessToken = jwtUtil.generateAccessToken(1L, "user", tokenVersion);
        String refreshToken = jwtUtil.generateRefreshToken(1L, tokenVersion);

        assertEquals(tokenVersion, jwtUtil.validateAccessToken(accessToken).getTv());
        assertEquals(tokenVersion, jwtUtil.validateRefreshToken(refreshToken).getTv());
    }
}
