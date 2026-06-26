package com.shm.core.mq;

import com.shm.common.model.dto.message.OrderEventMessage;
import com.shm.common.model.dto.message.ReportEventMessage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderEventPublisher 单元测试（Phase 14）
 *
 * <p>验证订单/举报事件发送到正确的 RocketMQ Topic，异常不向上抛出。
 */
@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(rocketMQTemplate);
    }

    @Test
    void publishOrderEvent_shouldSendToCorrectTopic() {
        OrderEventMessage message = OrderEventMessage.builder()
                .orderId(1L)
                .buyerId(10L)
                .sellerId(20L)
                .type("CONFIRMED")
                .title("订单确认")
                .content("买家已确认收货")
                .targetUid("20")
                .timestamp(System.currentTimeMillis())
                .build();

        publisher.publishOrderEvent(message);

        verify(rocketMQTemplate).syncSend(eq("shm-order-event"), any(Object.class));
    }

    @Test
    void publishOrderEvent_exception_shouldNotThrow() {
        OrderEventMessage message = OrderEventMessage.builder()
                .orderId(1L).type("CANCELLED")
                .title("订单取消").content("已取消")
                .targetUid("10").timestamp(System.currentTimeMillis())
                .build();
        doThrow(new RuntimeException("Broker unavailable"))
                .when(rocketMQTemplate).syncSend(anyString(), any(Object.class));

        // 不应抛出异常
        publisher.publishOrderEvent(message);

        verify(rocketMQTemplate).syncSend(anyString(), any(Object.class));
    }

    @Test
    void publishReportEvent_shouldSendToCorrectTopic() {
        ReportEventMessage message = ReportEventMessage.builder()
                .reportId(1L)
                .reporterId(10L)
                .reportedUserId(20L)
                .action("NEW_REPORT")
                .title("新举报")
                .content("有人举报了你的商品")
                .targetUids(List.of("admin1", "admin2"))
                .timestamp(System.currentTimeMillis())
                .build();

        publisher.publishReportEvent(message);

        verify(rocketMQTemplate).syncSend(eq("shm-report-event"), any(Object.class));
    }

    @Test
    void publishReportEvent_exception_shouldNotThrow() {
        ReportEventMessage message = ReportEventMessage.builder()
                .reportId(1L).action("RESOLVED")
                .title("举报处理").content("已处理")
                .targetUids(List.of("user1"))
                .timestamp(System.currentTimeMillis())
                .build();
        doThrow(new RuntimeException("Broker unavailable"))
                .when(rocketMQTemplate).syncSend(anyString(), any(Object.class));

        // 不应抛出异常
        publisher.publishReportEvent(message);

        verify(rocketMQTemplate).syncSend(anyString(), any(Object.class));
    }
}
