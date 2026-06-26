package com.shm.admin.mq;

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
 * ReportEventPublisher 单元测试（Phase 14）
 *
 * <p>验证举报事件发送到正确的 RocketMQ Topic，异常不向上抛出。
 */
@ExtendWith(MockitoExtension.class)
class ReportEventPublisherTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    private ReportEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ReportEventPublisher(rocketMQTemplate);
    }

    @Test
    void publishReportEvent_shouldSendToCorrectTopic() {
        ReportEventMessage message = ReportEventMessage.builder()
                .reportId(1L)
                .reporterId(10L)
                .reportedUserId(20L)
                .action("RESOLVED")
                .title("举报处理通知")
                .content("你的举报已处理完毕")
                .targetUids(List.of("10"))
                .timestamp(System.currentTimeMillis())
                .build();

        publisher.publishReportEvent(message);

        verify(rocketMQTemplate).syncSend(eq("shm-report-event"), any(Object.class));
    }

    @Test
    void publishReportEvent_exception_shouldNotThrow() {
        ReportEventMessage message = ReportEventMessage.builder()
                .reportId(1L).action("OFF_SHELF")
                .title("商品下架").content("已下架")
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
