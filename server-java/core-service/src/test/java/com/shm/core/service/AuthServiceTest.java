package com.shm.core.service;

import com.shm.common.exception.BusinessException;
import com.shm.common.model.dto.auth.LoginResponse;
import com.shm.common.model.dto.auth.RefreshResponse;
import com.shm.common.model.dto.auth.UserInfo;
import com.shm.common.model.entity.User;
import com.shm.common.util.JwtPayload;
import com.shm.common.util.JwtUtil;
import com.shm.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 单元测试
 *
 * <p>通过 Mockito mock 依赖（UserRepository / WeChatService / JwtUtil），
 * 测试 login / refresh / me 三条业务链路，无需 MySQL。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepo;
    @Mock
    private WeChatService weChatService;
    @Mock
    private JwtUtil jwtUtil;

    private AuthService authService;

    private static final String TEST_PHONE = "13800138000";
    private static final String MOCK_CODE = "mock_" + TEST_PHONE;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepo, weChatService, jwtUtil);
    }

    // ============================================================
    // login — 微信登录
    // ============================================================

    @Test
    void login_newUser_shouldAutoRegisterAndReturnTokens() {
        // Given: 微信返回手机号 → 用户不存在 → 自动注册
        when(weChatService.getPhoneNumber(MOCK_CODE)).thenReturn(TEST_PHONE);
        when(userRepo.findByPhone(TEST_PHONE)).thenReturn(null);
        when(userRepo.create(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtUtil.generateAccessToken(eq(1L), eq("user"), anyInt())).thenReturn("access-token-1");
        when(jwtUtil.generateRefreshToken(eq(1L), anyInt())).thenReturn("refresh-token-1");

        // When
        LoginResponse resp = authService.login(MOCK_CODE);

        // Then
        assertNotNull(resp);
        assertEquals("access-token-1", resp.getAccessToken());
        assertEquals("refresh-token-1", resp.getRefreshToken());
        assertTrue(resp.isNewUser());
        assertNotNull(resp.getUser());
        assertEquals(1L, resp.getUser().getId());
        assertEquals(TEST_PHONE, resp.getUser().getPhone());
        assertEquals("微信用户", resp.getUser().getNickname());
        assertEquals("user", resp.getUser().getRole());
        assertEquals(100, resp.getUser().getCreditScore());

        verify(userRepo).create(any(User.class));
    }

    @Test
    void login_existingUser_shouldReturnTokens() {
        // Given: 用户已存在
        User existing = User.builder()
                .id(2L).phone(TEST_PHONE).nickname("老用户").role("cs")
                .status("active").tokenVersion(3).creditScore(90).build();
        when(weChatService.getPhoneNumber(MOCK_CODE)).thenReturn(TEST_PHONE);
        when(userRepo.findByPhone(TEST_PHONE)).thenReturn(existing);
        when(jwtUtil.generateAccessToken(eq(2L), eq("cs"), eq(3))).thenReturn("access-token-2");
        when(jwtUtil.generateRefreshToken(eq(2L), eq(3))).thenReturn("refresh-token-2");

        // When
        LoginResponse resp = authService.login(MOCK_CODE);

        // Then
        assertFalse(resp.isNewUser());
        assertEquals("access-token-2", resp.getAccessToken());
        assertEquals(2L, resp.getUser().getId());
        assertEquals("cs", resp.getUser().getRole());

        // 不应创建新用户
        verify(userRepo, never()).create(any(User.class));
    }

    @Test
    void login_bannedUser_shouldThrowBusinessException() {
        // Given: 用户已被封禁
        User banned = User.builder()
                .id(3L).phone(TEST_PHONE).status("banned").build();
        when(weChatService.getPhoneNumber(MOCK_CODE)).thenReturn(TEST_PHONE);
        when(userRepo.findByPhone(TEST_PHONE)).thenReturn(banned);

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () ->
                authService.login(MOCK_CODE));

        assertEquals(1004, ex.getCode());
    }

    @Test
    void login_wechatServiceThrows_shouldRethrowBusinessException() {
        // Given: 微信 API 调用失败
        when(weChatService.getPhoneNumber(MOCK_CODE))
                .thenThrow(new BusinessException(6003, "微信 API 调用失败"));

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () ->
                authService.login(MOCK_CODE));

        assertEquals(6003, ex.getCode());
    }

    // ============================================================
    // refresh — Token 刷新
    // ============================================================

    @Test
    void refresh_validToken_shouldReturnNewTokens() {
        // Given: 有效的 refresh token
        JwtPayload payload = new JwtPayload(1L, null, 0, "refresh");
        User user = User.builder()
                .id(1L).role("user").status("active").tokenVersion(0).build();
        when(jwtUtil.validateRefreshToken("valid-refresh-token")).thenReturn(payload);
        when(userRepo.findById(1L)).thenReturn(user);
        when(jwtUtil.generateAccessToken(eq(1L), eq("user"), eq(0))).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(eq(1L), eq(0))).thenReturn("new-refresh");

        // When
        RefreshResponse resp = authService.refresh("valid-refresh-token");

        // Then
        assertEquals("new-access", resp.getAccessToken());
        assertEquals("new-refresh", resp.getRefreshToken());
    }

    @Test
    void refresh_invalidToken_shouldThrowBusinessException() {
        // Given: Token 验证失败
        when(jwtUtil.validateRefreshToken("bad-token"))
                .thenThrow(new RuntimeException("签名错误"));

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () ->
                authService.refresh("bad-token"));

        assertEquals(1002, ex.getCode());
        assertTrue(ex.getMessage().contains("重新登录"));
    }

    @Test
    void refresh_notRefreshType_shouldThrowBusinessException() {
        // Given: 用 access token 来刷 refresh（type != "refresh"）
        JwtPayload payload = new JwtPayload(1L, "user", 0, null);
        when(jwtUtil.validateRefreshToken("access-token")).thenReturn(payload);

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () ->
                authService.refresh("access-token"));

        assertEquals(1002, ex.getCode());
    }

    @Test
    void refresh_tokenVersionMismatch_shouldThrowBusinessException() {
        // Given: tokenVersion 不匹配（用户在别处登录导致旧 token 失效）
        JwtPayload payload = new JwtPayload(1L, null, 1, "refresh");
        User user = User.builder()
                .id(1L).status("active").tokenVersion(2).build(); // DB 中已是 2
        when(jwtUtil.validateRefreshToken("old-token")).thenReturn(payload);
        when(userRepo.findById(1L)).thenReturn(user);

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () ->
                authService.refresh("old-token"));

        assertEquals(1003, ex.getCode());
    }

    @Test
    void refresh_bannedUser_shouldThrowBusinessException() {
        // Given: 用户已被封禁
        JwtPayload payload = new JwtPayload(1L, null, 0, "refresh");
        User user = User.builder()
                .id(1L).status("banned").tokenVersion(0).build();
        when(jwtUtil.validateRefreshToken("banned-user-token")).thenReturn(payload);
        when(userRepo.findById(1L)).thenReturn(user);

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () ->
                authService.refresh("banned-user-token"));

        assertEquals(1004, ex.getCode());
    }

    @Test
    void refresh_userNotFound_shouldThrowBusinessException() {
        // Given: Token 中的用户 ID 在数据库中不存在
        JwtPayload payload = new JwtPayload(999L, null, 0, "refresh");
        when(jwtUtil.validateRefreshToken("ghost-token")).thenReturn(payload);
        when(userRepo.findById(999L)).thenReturn(null);

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () ->
                authService.refresh("ghost-token"));

        assertEquals(1002, ex.getCode());
    }

    // ============================================================
    // me — 获取当前用户信息
    // ============================================================

    @Test
    void me_existingUser_shouldReturnUserInfo() {
        // Given
        User user = User.builder()
                .id(1L).phone("138****3800").nickname("小明").avatar("/avatar.png")
                .className("计科2301").dormBuilding("A栋").role("user")
                .creditScore(85).build();
        when(userRepo.findById(1L)).thenReturn(user);

        // When
        UserInfo info = authService.me(1L);

        // Then
        assertEquals(1L, info.getId());
        assertEquals("小明", info.getNickname());
        assertEquals("计科2301", info.getClassName());
        assertEquals("A栋", info.getDormBuilding());
    }

    @Test
    void me_userNotFound_shouldThrowBusinessException() {
        // Given
        when(userRepo.findById(999L)).thenReturn(null);

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () ->
                authService.me(999L));

        assertEquals(1002, ex.getCode());
    }
}
