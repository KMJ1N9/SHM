package com.shm.im.mq;

import com.shm.common.model.dto.message.OrderEventMessage;
import com.shm.im.service.TencentImService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ImPushOrderConsumer 单元测试（Phase 14）
 *
 * <p>验证消费 OrderEventMessage 后调用腾讯云 IM 发送系统消息。
 */
@ExtendWith(MockitoExtension.class)
class ImPushOrderConsumerTest {

    @Mock
    private TencentImService tencentImService;

    private ImPushOrderConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ImPushOrderConsumer(tencentImService);
    }

    @Test
    void onMessage_shouldCallSendSystemMessage() {
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

        verify(tencentImService).sendSystemMessage(
                eq("20"), eq("订单已确认"), eq("买家已确认收货"), anyMap());
    }

    @Test
    void onMessage_imFail_shouldThrowRuntimeException() {
        OrderEventMessage message = OrderEventMessage.builder()
                .orderId(1L).type("CANCELLED")
                .title("订单取消").content("已取消")
                .targetUid("10").timestamp(System.currentTimeMillis())
                .build();
        doThrow(new RuntimeException("IM API error"))
                .when(tencentImService).sendSystemMessage(anyString(), anyString(), anyString(), any());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> consumer.onMessage(message));

        assertTrue(ex.getMessage().contains("IM 推送失败"));
    }
}
