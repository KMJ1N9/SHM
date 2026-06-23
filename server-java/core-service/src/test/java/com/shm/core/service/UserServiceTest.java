package com.shm.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shm.common.constant.CacheConstants;
import com.shm.common.constant.RedisKeys;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.dto.user.ContactDTO;
import com.shm.common.model.dto.user.UpdateProfileRequest;
import com.shm.common.model.entity.User;
import com.shm.common.util.SensitiveWordFilter;
import com.shm.core.repository.ReviewRepository;
import com.shm.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试（Phase 11 11.1.5a）
 *
 * <p>覆盖公开信息查询（含缓存）/客服管理员联系/资料编辑，Mock 依赖层。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepo;
    @Mock
    private ReviewRepository reviewRepo;
    @Mock
    private SensitiveWordFilter sensitiveFilter;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepo, reviewRepo, sensitiveFilter, redis, objectMapper);
    }

    /** 仅在需要 Redis 缓存的测试中启用 valueOps mock */
    private void mockRedisValueOps() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    // ============================================================
    // getPublicProfile — 用户公开信息
    // ============================================================

    @Test
    void getPublicProfile_cacheMiss_shouldQueryDbAndCache() throws Exception {
        mockRedisValueOps();
        String cacheKey = RedisKeys.userPublicKey(5L);
        when(valueOps.get(cacheKey)).thenReturn(null);

        User user = User.builder()
                .id(5L).nickname("测试用户").creditScore(88)
                .className("计科2301").dormBuilding("A栋")
                .avatar("/av.png").role("user").status("active")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(userRepo.findPublicById(5L)).thenReturn(user);
        when(reviewRepo.getAvgScores(5L)).thenReturn(Map.of(
                "total", 3L, "avg_communication", 4.5,
                "avg_punctuality", 4.0, "avg_accuracy", 4.2));

        Map<String, Object> result = userService.getPublicProfile(5L);

        assertNotNull(result);
        assertNotNull(result.get("user"));
        assertNotNull(result.get("review_summary"));

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("review_summary");
        assertEquals(3L, summary.get("total"));
        assertEquals(4.5, summary.get("avg_communication"));

        verify(valueOps).set(eq(cacheKey), anyString(), anyLong(), any());
    }

    @Test
    void getPublicProfile_userNotFound_shouldThrowBusinessException() {
        mockRedisValueOps();
        String cacheKey = RedisKeys.userPublicKey(999L);
        when(valueOps.get(cacheKey)).thenReturn(null);
        when(userRepo.findPublicById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.getPublicProfile(999L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        verify(valueOps).set(eq(cacheKey), eq(RedisKeys.EMPTY_PREFIX),
                eq((long) CacheConstants.EMPTY_VALUE_TTL_SECONDS), any());
    }

    @Test
    void getPublicProfile_cacheHit_shouldReturnCachedResult() throws Exception {
        mockRedisValueOps();
        String cacheKey = RedisKeys.userPublicKey(5L);
        String cachedJson = objectMapper.writeValueAsString(Map.of(
                "user", Map.of("id", 5L, "nickname", "缓存用户"),
                "review_summary", Map.of("total", 1L)));

        when(valueOps.get(cacheKey)).thenReturn(cachedJson);

        Map<String, Object> result = userService.getPublicProfile(5L);

        assertNotNull(result);
        verify(userRepo, never()).findPublicById(anyLong());
    }

    @Test
    void getPublicProfile_emptyValueCache_shouldThrowNotFound() {
        mockRedisValueOps();
        String cacheKey = RedisKeys.userPublicKey(999L);
        when(valueOps.get(cacheKey)).thenReturn(RedisKeys.EMPTY_PREFIX);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.getPublicProfile(999L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ============================================================
    // getCSContact — 客服联系方式
    // ============================================================

    @Test
    void getCSContact_success_shouldReturnContact() {
        User cs = User.builder()
                .id(1L).nickname("客服小助手").avatar("/cs.png").build();
        when(userRepo.findCSUser()).thenReturn(cs);

        ContactDTO result = userService.getCSContact();

        assertEquals(1L, result.getId());
        assertEquals("客服小助手", result.getNickname());
        assertEquals("/cs.png", result.getAvatar());
    }

    @Test
    void getCSContact_notFound_shouldThrowBusinessException() {
        when(userRepo.findCSUser()).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.getCSContact());

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ============================================================
    // getAdminContact — 管理员联系方式
    // ============================================================

    @Test
    void getAdminContact_success_shouldReturnContact() {
        User admin = User.builder()
                .id(2L).nickname("系统管理员").avatar(null).build();
        when(userRepo.findAdminUser()).thenReturn(admin);

        ContactDTO result = userService.getAdminContact();

        assertEquals(2L, result.getId());
        assertEquals("系统管理员", result.getNickname());
        assertEquals("", result.getAvatar()); // null → ""
    }

    @Test
    void getAdminContact_notFound_shouldThrowBusinessException() {
        when(userRepo.findAdminUser()).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.getAdminContact());

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ============================================================
    // updateProfile — 编辑个人资料
    // ============================================================

    @Test
    void updateProfile_success_shouldUpdateAndEvictCache() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setNickname("新昵称");
        req.setClassName("计科2302");

        when(sensitiveFilter.containsSensitive("新昵称")).thenReturn(false);
        when(userRepo.updateProfile(any(User.class))).thenReturn(1);

        User updated = User.builder()
                .id(10L).nickname("新昵称").className("计科2302")
                .dormBuilding("B栋").avatar("/a.png").build();
        when(userRepo.findById(10L)).thenReturn(updated);

        Map<String, Object> result = userService.updateProfile(10L, req, "user");

        assertEquals("新昵称", result.get("nickname"));
        assertEquals("计科2302", result.get("class_name"));
        verify(redis).delete(RedisKeys.userPublicKey(10L));
    }

    @Test
    void updateProfile_sensitiveWord_shouldThrowBusinessException() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setNickname("敏感词昵称");

        when(sensitiveFilter.containsSensitive("敏感词昵称")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.updateProfile(10L, req, "user"));

        assertEquals(ErrorCode.SENSITIVE_WORD.getCode(), ex.getCode());
    }

    @Test
    void updateProfile_userNotFound_shouldThrowBusinessException() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setClassName("新班级");

        when(userRepo.updateProfile(any(User.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.updateProfile(10L, req, "user"));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void updateProfile_noNicknameChange_shouldSkipSensitiveCheck() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setClassName("新班级");

        when(userRepo.updateProfile(any(User.class))).thenReturn(1);
        // 提供完整字段避免 Map.of() NPE（avatar/dorm_building 在 DB 中可能为 null）
        User updated = User.builder()
                .id(10L).nickname("旧昵称").className("新班级")
                .avatar("").dormBuilding("").build();
        when(userRepo.findById(10L)).thenReturn(updated);

        userService.updateProfile(10L, req, "user");

        verify(sensitiveFilter, never()).containsSensitive(anyString());
    }

    @Test
    void updateProfile_reservedName_shouldThrowForNormalUser() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setNickname("我是管理员");

        when(sensitiveFilter.containsSensitive("我是管理员")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.updateProfile(10L, req, "user"));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
    }

    @Test
    void updateProfile_reservedName_shouldPassForAdmin() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setNickname("平台管理员");
        req.setClassName("管理员");

        when(sensitiveFilter.containsSensitive("平台管理员")).thenReturn(false);
        when(userRepo.updateProfile(any(User.class))).thenReturn(1);
        User updated = User.builder()
                .id(10L).nickname("平台管理员").className("管理员")
                .avatar("").dormBuilding("").build();
        when(userRepo.findById(10L)).thenReturn(updated);

        // admin 用户可以使用保留名
        Map<String, Object> result = userService.updateProfile(10L, req, "admin");

        assertEquals("平台管理员", result.get("nickname"));
    }

    @Test
    void updateProfile_reservedName_shouldPassForCS() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setNickname("客服小助手");

        when(sensitiveFilter.containsSensitive("客服小助手")).thenReturn(false);
        when(userRepo.updateProfile(any(User.class))).thenReturn(1);
        User updated = User.builder()
                .id(10L).nickname("客服小助手")
                .className("").avatar("").dormBuilding("").build();
        when(userRepo.findById(10L)).thenReturn(updated);

        // cs 用户可以使用保留名
        Map<String, Object> result = userService.updateProfile(10L, req, "cs");

        assertEquals("客服小助手", result.get("nickname"));
    }
}
