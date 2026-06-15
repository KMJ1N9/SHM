package com.shm.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.entity.Notification;
import com.shm.common.model.entity.User;

import java.time.LocalDateTime;
import com.shm.core.config.CreditProperties;
import com.shm.core.repository.NotificationRepository;
import com.shm.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CreditService 单元测试（Phase 11 11.1.5f）
 *
 * <p>覆盖信誉分查询/变动记录/公开查询/变动写入，Mock Repository 层。
 */
@ExtendWith(MockitoExtension.class)
class CreditServiceTest {

    @Mock
    private UserRepository userRepo;
    @Mock
    private NotificationRepository notificationRepo;
    @Mock
    private CreditProperties creditProps;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private CreditService creditService;

    @BeforeEach
    void setUp() {
        creditService = new CreditService(userRepo, notificationRepo, objectMapper, creditProps);
    }

    // ============================================================
    // my — 我的信誉分 + 变动记录
    // ============================================================

    @Test
    void my_success_shouldReturnScoreAndChangeLog() {
        // Given
        User user = User.builder().id(1L).creditScore(95).build();
        when(userRepo.findById(1L)).thenReturn(user);

        List<Notification> logs = List.of(
                Notification.builder().id(1L).userId(1L).type("credit_change")
                        .content("交易奖励 +2").metadata("{\"delta\":2}")
                        .createdAt(LocalDateTime.now()).build(),
                Notification.builder().id(2L).userId(1L).type("credit_change")
                        .content("发布奖励").metadata("{\"delta\":1}")
                        .createdAt(LocalDateTime.now()).build()
        );
        when(notificationRepo.listByUserId(eq(1L), eq("credit_change"), eq(0), eq(20)))
                .thenReturn(logs);
        when(notificationRepo.countByUserId(eq(1L), eq("credit_change"))).thenReturn(2L);

        // When
        Map<String, Object> result = creditService.my(1L, 1, 20);

        // Then
        assertEquals(95, result.get("score"));
        assertEquals(2L, result.get("total"));
        assertEquals(1, result.get("page"));
        assertEquals(20, result.get("pageSize"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changeLog = (List<Map<String, Object>>) result.get("change_log");
        assertEquals(2, changeLog.size());
        assertNotNull(changeLog.get(0).get("reason"));
        assertNotNull(changeLog.get(0).get("created_at"));
    }

    @Test
    void my_userNotFound_shouldThrowBusinessException() {
        when(userRepo.findById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                creditService.my(999L, 1, 20));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void my_shouldCalculatePaginationOffset() {
        User user = User.builder().id(1L).creditScore(80).build();
        when(userRepo.findById(1L)).thenReturn(user);
        when(notificationRepo.listByUserId(eq(1L), eq("credit_change"), eq(10), eq(5)))
                .thenReturn(List.of());
        when(notificationRepo.countByUserId(eq(1L), eq("credit_change"))).thenReturn(0L);

        creditService.my(1L, 3, 5);

        // offset = (3-1)*5 = 10
        verify(notificationRepo).listByUserId(eq(1L), eq("credit_change"), eq(10), eq(5));
    }

    // ============================================================
    // userPublic — 公开信誉分
    // ============================================================

    @Test
    void userPublic_success_shouldReturnScore() {
        User user = User.builder().id(5L).creditScore(88).build();
        when(userRepo.findPublicById(5L)).thenReturn(user);

        Map<String, Object> result = creditService.userPublic(5L);

        assertEquals(5L, result.get("user_id"));
        assertEquals(88, result.get("score"));
    }

    @Test
    void userPublic_notFound_shouldThrowBusinessException() {
        when(userRepo.findPublicById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                creditService.userPublic(999L));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    // ============================================================
    // changeScore — 信誉分变动（内部调用）
    // ============================================================

    @Test
    void changeScore_positive_shouldIncreaseScore() {
        // Given
        User user = User.builder().id(1L).creditScore(90).build();
        User updated = User.builder().id(1L).creditScore(95).build();
        when(userRepo.findById(1L)).thenReturn(user).thenReturn(updated);
        when(creditProps.getMax()).thenReturn(200);

        // When
        Map<String, Object> result = creditService.changeScore(1L, 5, "交易奖励", null);

        // Then
        assertEquals(1L, result.get("user_id"));
        assertEquals(5, result.get("delta"));
        assertEquals(90, result.get("previous_score"));
        assertEquals(95, result.get("current_score"));
        assertEquals("交易奖励", result.get("reason"));

        verify(userRepo).updateCreditScore(eq(1L), eq(5), eq(200));
        verify(notificationRepo).insert(any(Notification.class));
    }

    @Test
    void changeScore_negative_shouldDecreaseScore() {
        // Given
        User user = User.builder().id(2L).creditScore(50).build();
        User updated = User.builder().id(2L).creditScore(45).build();
        when(userRepo.findById(2L)).thenReturn(user).thenReturn(updated);
        when(creditProps.getMax()).thenReturn(200);

        // When
        Map<String, Object> result = creditService.changeScore(2L, -5, "差评扣分", 100L);

        // Then
        assertEquals(-5, result.get("delta"));
        assertEquals(50, result.get("previous_score"));
        assertEquals(45, result.get("current_score"));
        verify(userRepo).updateCreditScore(eq(2L), eq(-5), eq(200));
        verify(notificationRepo).insert(any(Notification.class));
    }

    @Test
    void changeScore_withRefId_shouldIncludeInNotificationMetadata() {
        User user = User.builder().id(3L).creditScore(70).build();
        User updated = User.builder().id(3L).creditScore(71).build();
        when(userRepo.findById(3L)).thenReturn(user).thenReturn(updated);
        when(creditProps.getMax()).thenReturn(200);

        creditService.changeScore(3L, 1, "发布商品", 42L);

        // ref_id 写入通知 metadata（非返回 Map 的顶级字段）
        verify(notificationRepo).insert(argThat(n ->
                n.getMetadata() != null && n.getMetadata().contains("\"ref_id\":42")));
    }

    @Test
    void changeScore_userNotFound_shouldThrowBusinessException() {
        when(userRepo.findById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                creditService.changeScore(999L, 5, "test", null));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }
}
