package com.shm.core.service;

import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.entity.Notification;
import com.shm.core.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NotificationService 单元测试（Phase 11 11.1.5e）
 *
 * <p>覆盖通知列表/未读数/已读/全部已读，Mock NotificationRepository。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepo;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepo);
    }

    // ============================================================
    // list — 通知列表
    // ============================================================

    @Test
    void list_shouldReturnPaginatedNotifications() {
        // Given
        List<Notification> notifications = List.of(
                buildNotification(1L, 10L, "order_update", "新订单", "有人想买你的商品"),
                buildNotification(2L, 10L, "system", "系统通知", "欢迎使用")
        );
        when(notificationRepo.listByUserId(eq(10L), eq("order_update"), eq(0), eq(20)))
                .thenReturn(notifications);
        when(notificationRepo.countByUserId(eq(10L), eq("order_update"))).thenReturn(2L);

        // When
        Map<String, Object> result = notificationService.list(10L, "order_update", 1, 20);

        // Then
        assertNotNull(result);
        assertEquals(2L, result.get("total"));
        assertEquals(1, result.get("page"));
        assertEquals(20, result.get("pageSize"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
        assertEquals(2, list.size());

        // 字段完整性
        Map<String, Object> first = list.get(0);
        assertEquals(1L, first.get("id"));
        assertEquals("order_update", first.get("type"));
        assertEquals("新订单", first.get("title"));
        assertEquals("有人想买你的商品", first.get("content"));
        assertEquals(false, first.get("is_read"));
    }

    @Test
    void list_shouldRespectPaginationOffset() {
        // Given
        when(notificationRepo.listByUserId(eq(10L), eq(null), eq(10), eq(5)))
                .thenReturn(List.of());
        when(notificationRepo.countByUserId(eq(10L), eq(null))).thenReturn(0L);

        // When
        Map<String, Object> result = notificationService.list(10L, null, 3, 5);

        // Then: offset = (3-1)*5 = 10
        verify(notificationRepo).listByUserId(eq(10L), isNull(), eq(10), eq(5));
        assertEquals(0L, result.get("total"));
        assertEquals(3, result.get("page"));
    }

    // ============================================================
    // unreadCount — 未读计数
    // ============================================================

    @Test
    void unreadCount_shouldReturnCount() {
        when(notificationRepo.countUnread(10L)).thenReturn(5L);

        Map<String, Object> result = notificationService.unreadCount(10L);

        assertEquals(5L, result.get("count"));
        verify(notificationRepo).countUnread(10L);
    }

    @Test
    void unreadCount_zero_shouldReturnZero() {
        when(notificationRepo.countUnread(10L)).thenReturn(0L);

        Map<String, Object> result = notificationService.unreadCount(10L);

        assertEquals(0L, result.get("count"));
    }

    // ============================================================
    // read — 标记已读
    // ============================================================

    @Test
    void read_success_shouldNotThrow() {
        when(notificationRepo.markRead(1L, 10L)).thenReturn(1);

        assertDoesNotThrow(() -> notificationService.read(1L, 10L));
        verify(notificationRepo).markRead(1L, 10L);
    }

    @Test
    void read_notFound_shouldThrowBusinessException() {
        when(notificationRepo.markRead(999L, 10L)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                notificationService.read(999L, 10L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("通知不存在"));
    }

    // ============================================================
    // readAll — 全部已读
    // ============================================================

    @Test
    void readAll_shouldReturnUpdatedCount() {
        when(notificationRepo.markAllRead(10L)).thenReturn(7);

        Map<String, Object> result = notificationService.readAll(10L);

        assertEquals(7, result.get("updated"));
        verify(notificationRepo).markAllRead(10L);
    }

    @Test
    void readAll_zero_shouldReturnZero() {
        when(notificationRepo.markAllRead(10L)).thenReturn(0);

        Map<String, Object> result = notificationService.readAll(10L);

        assertEquals(0, result.get("updated"));
    }

    // ---- 辅助 ----

    private Notification buildNotification(Long id, Long userId, String type,
                                           String title, String content) {
        return Notification.builder()
                .id(id).userId(userId).type(type).title(title)
                .content(content).isRead(false)
                .metadata("{}")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
