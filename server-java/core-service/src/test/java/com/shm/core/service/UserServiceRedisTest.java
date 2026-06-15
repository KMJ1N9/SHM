package com.shm.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.common.constant.CacheConstants;
import com.shm.common.constant.RedisKeys;
import com.shm.common.exception.BusinessException;
import com.shm.common.model.entity.User;
import com.shm.core.repository.ReviewRepository;
import com.shm.core.repository.UserRepository;
import com.shm.common.util.SensitiveWordFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserService Redis 缓存层单元测试（Phase 10 P1-2）
 *
 * <p>覆盖 Cache-Aside 模式 + 缓存清除：
 * <ol>
 *   <li>缓存命中 → 返回缓存数据，不查 DB</li>
 *   <li>缓存未命中 → 查 DB + 写缓存</li>
 *   <li>空值缓存命中 → 直接抛 NOT_FOUND（防穿透）</li>
 *   <li>缓存读取异常 → 降级查 DB</li>
 *   <li>缓存写入失败 → 不阻塞业务</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceRedisTest {

    @Mock
    private UserRepository userRepo;
    @Mock
    private ReviewRepository reviewRepo;
    @Mock
    private SensitiveWordFilter sensitiveFilter;
    @Mock
    private StringRedisTemplate redis;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepo, reviewRepo, sensitiveFilter, redis, objectMapper);
    }

    // ============================================================
    // 缓存命中
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void getPublicProfile_cacheHit_shouldReturnCachedData() throws Exception {
        // Given: 缓存中有用户数据
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        Map<String, Object> cached = Map.of(
                "user", Map.of("id", 1, "nickname", "小明", "avatar", "/a.png"),
                "review_summary", Map.of("total", 5, "avg_communication", 4.5,
                        "avg_punctuality", 4.0, "avg_accuracy", 5.0));
        String json = objectMapper.writeValueAsString(cached);
        when(valueOps.get(anyString())).thenReturn(json);

        // When
        Map<String, Object> result = userService.getPublicProfile(1L);

        // Then: 返回缓存数据
        assertNotNull(result);
        assertTrue(result.containsKey("user"));
        assertTrue(result.containsKey("review_summary"));

        // 不应查 DB
        verify(userRepo, never()).findPublicById(anyLong());
        verify(reviewRepo, never()).getAvgScores(anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPublicProfile_cacheHit_shouldReturnCorrectUserData() throws Exception {
        // Given
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        Map<String, Object> cached = Map.of(
                "user", Map.of("id", 42, "nickname", "测试用户"),
                "review_summary", Map.of("total", 3));
        when(valueOps.get(anyString())).thenReturn(objectMapper.writeValueAsString(cached));

        // When
        Map<String, Object> result = userService.getPublicProfile(42L);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) result.get("user");
        assertEquals(42, user.get("id"));
        assertEquals("测试用户", user.get("nickname"));
    }

    // ============================================================
    // 缓存未命中 → 查 DB + 写缓存
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void getPublicProfile_cacheMiss_shouldQueryDbAndWriteCache() throws Exception {
        // Given: 缓存为空
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        // DB 有用户
        User user = User.builder()
                .id(10L).nickname("卖家C").avatar("/c.png")
                .creditScore(90).className("计科2302").dormBuilding("B栋")
                .status("active").build();
        when(userRepo.findPublicById(10L)).thenReturn(user);

        // 评价数据
        when(reviewRepo.getAvgScores(10L)).thenReturn(Map.of(
                "total", 2L, "avg_communication", 5.0,
                "avg_punctuality", 4.5, "avg_accuracy", 5.0));

        // When
        Map<String, Object> result = userService.getPublicProfile(10L);

        // Then: 查了 DB
        verify(userRepo).findPublicById(10L);
        verify(reviewRepo).getAvgScores(10L);

        // 写入了缓存
        verify(valueOps).set(anyString(), anyString(), anyLong(), any());

        // 返回正确
        assertNotNull(result);
    }

    // ============================================================
    // 用户不存在 → 空值缓存（防穿透）
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void getPublicProfile_userNotFound_shouldCacheEmptyMarkerAndThrow() {
        // Given: 缓存为空，DB 也不存在该用户
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        when(userRepo.findPublicById(999L)).thenReturn(null);

        // When & Then: 应抛异常
        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.getPublicProfile(999L));

        assertEquals("用户不存在", ex.getMessage());

        // 写入了空值标记（防穿透）
        verify(valueOps).set(anyString(), eq(RedisKeys.EMPTY_PREFIX),
                eq(CacheConstants.EMPTY_VALUE_TTL_SECONDS), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPublicProfile_emptyMarkerHit_shouldThrowWithoutQueryingDb() {
        // Given: 缓存中是空值标记
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(RedisKeys.EMPTY_PREFIX);

        // When & Then: 直接抛异常，不查 DB
        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.getPublicProfile(999L));

        assertEquals("用户不存在", ex.getMessage());
        verify(userRepo, never()).findPublicById(anyLong());
    }

    // ============================================================
    // 缓存读取异常 → 降级查 DB
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void getPublicProfile_cacheReadError_shouldFallbackToDb() {
        // Given: 缓存读取异常
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        // DB 正常
        User user = User.builder().id(1L).nickname("降级用户").creditScore(80)
                .status("active").build();
        when(userRepo.findPublicById(1L)).thenReturn(user);
        when(reviewRepo.getAvgScores(1L)).thenReturn(Map.of("total", 0L));

        // When: 不应抛异常
        Map<String, Object> result = userService.getPublicProfile(1L);

        // Then: 降级成功
        assertNotNull(result);
        verify(userRepo).findPublicById(1L);
    }

    // ============================================================
    // 缓存写入失败 → 不阻塞业务
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void getPublicProfile_cacheWriteError_shouldStillReturnData() {
        // Given: 缓存读取失败，DB 正常，但缓存写入也失败
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        // 缓存写入抛异常
        doThrow(new RuntimeException("Redis set error"))
                .when(valueOps).set(anyString(), anyString(), anyLong(), any());

        User user = User.builder().id(1L).nickname("容错用户").creditScore(70)
                .status("active").build();
        when(userRepo.findPublicById(1L)).thenReturn(user);
        when(reviewRepo.getAvgScores(1L)).thenReturn(Map.of("total", 1L));

        // When: 不应抛异常（缓存写入失败不影响业务）
        Map<String, Object> result = userService.getPublicProfile(1L);

        // Then: 仍然返回 DB 数据
        assertNotNull(result);
    }

    // ============================================================
    // 空值缓存命中后不阻塞写操作
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void getPublicProfile_emptyCacheWriteFailure_shouldStillThrow() {
        // Given: 用户不存在，但空值写入失败（不应影响异常抛出）
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepo.findPublicById(999L)).thenReturn(null);
        // 空值写入也失败
        doThrow(new RuntimeException("Redis set error"))
                .when(valueOps).set(anyString(), anyString(), anyLong(), any());

        // When & Then: 仍然抛异常（核心业务逻辑不受缓存影响）
        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.getPublicProfile(999L));

        assertEquals("用户不存在", ex.getMessage());
    }
}
