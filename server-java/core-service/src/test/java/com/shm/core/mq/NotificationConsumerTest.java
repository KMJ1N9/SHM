package com.shm.core.mq;

import com.shm.common.model.dto.message.OrderEventMessage;
import com.shm.common.model.entity.Notification;
import com.shm.core.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * NotificationConsumer 单元测试（Phase 14）
 *
 * <p>验证消费 OrderEventMessage 后正确写入站内通知表。
 */
@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationConsumer(notificationRepository);
    }

    @Test
    void onMessage_shouldInsertNotification() {
        OrderEventMessage message = OrderEventMessage.builder()
                .orderId(1L)
                .buyerId(10L)
                .sellerId(20L)
                .type("CONFIRMED")
                .title("订单已确认")
                .content("买家已确认收货")
                .targetUid("20")
                .timestamp(System.currentTimeMillis())
                .build();

        consumer.onMessage(message);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).insert(captor.capture());

        Notification notification = captor.getValue();
        assertEquals(20L, notification.getUserId());
        assertEquals("order_update", notification.getType());
        assertEquals("订单已确认", notification.getTitle());
        assertEquals("买家已确认收货", notification.getContent());
        assertFalse(notification.getIsRead());
        assertTrue(notification.getMetadata().contains("1"));
    }

    @Test
    void onMessage_insertFail_shouldThrowRuntimeException() {
        OrderEventMessage message = OrderEventMessage.builder()
                .orderId(1L).type("CANCELLED")
                .title("订单取消").content("已取消")
                .targetUid("10").timestamp(System.currentTimeMillis())
                .build();
        doThrow(new RuntimeException("DB error"))
                .when(notificationRepository).insert(any());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> consumer.onMessage(message));

        assertTrue(ex.getMessage().contains("通知写入失败"));
    }
}
