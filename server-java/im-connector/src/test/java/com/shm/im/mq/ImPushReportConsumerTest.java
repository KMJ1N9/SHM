package com.shm.im.mq;

import com.shm.common.model.dto.message.ReportEventMessage;
import com.shm.im.service.TencentImService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ImPushReportConsumer 单元测试（Phase 14）
 *
 * <p>验证消费 ReportEventMessage 后批量推送 IM，单个失败不阻塞其他用户。
 */
@ExtendWith(MockitoExtension.class)
class ImPushReportConsumerTest {

    @Mock
    private TencentImService tencentImService;

    private ImPushReportConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ImPushReportConsumer(tencentImService);
    }

    @Test
    void onMessage_shouldPushToAllTargets() {
        ReportEventMessage message = ReportEventMessage.builder()
                .reportId(1L)
                .reporterId(10L)
                .reportedUserId(20L)
                .action("RESOLVED")
                .title("举报处理")
                .content("你的举报已处理")
                .targetUids(List.of("10", "20"))
                .timestamp(System.currentTimeMillis())
                .build();

        consumer.onMessage(message);

        verify(tencentImService).sendSystemMessage(
                eq("10"), eq("举报处理"), eq("你的举报已处理"), anyMap());
        verify(tencentImService).sendSystemMessage(
                eq("20"), eq("举报处理"), eq("你的举报已处理"), anyMap());
    }

    @Test
    void onMessage_singleFail_shouldContinueToNextTarget() {
        ReportEventMessage message = ReportEventMessage.builder()
                .reportId(1L)
                .action("OFF_SHELF")
                .title("商品下架")
                .content("你的商品已被下架")
                .targetUids(List.of("user1", "user2", "user3"))
                .timestamp(System.currentTimeMillis())
                .build();

        // user1 失败
        doThrow(new RuntimeException("IM error for user1"))
                .when(tencentImService).sendSystemMessage(eq("user1"), anyString(), anyString(), any());

        consumer.onMessage(message);

        // user2 和 user3 仍然被推送
        verify(tencentImService).sendSystemMessage(eq("user1"), anyString(), anyString(), any());
        verify(tencentImService).sendSystemMessage(eq("user2"), anyString(), anyString(), any());
        verify(tencentImService).sendSystemMessage(eq("user3"), anyString(), anyString(), any());
    }

    @Test
    void onMessage_emptyTargets_shouldSkip() {
        ReportEventMessage message = ReportEventMessage.builder()
                .reportId(1L)
                .action("NEW_REPORT")
                .title("新举报")
                .content("有新的举报")
                .targetUids(List.of())
                .timestamp(System.currentTimeMillis())
                .build();

        consumer.onMessage(message);

        verify(tencentImService, never()).sendSystemMessage(anyString(), anyString(), anyString(), any());
    }

    @Test
    void onMessage_nullTargets_shouldSkip() {
        ReportEventMessage message = ReportEventMessage.builder()
                .reportId(1L)
                .action("NEW_REPORT")
                .title("新举报")
                .content("有新的举报")
                .targetUids(null)
                .timestamp(System.currentTimeMillis())
                .build();

        consumer.onMessage(message);

        verify(tencentImService, never()).sendSystemMessage(anyString(), anyString(), anyString(), any());
    }
}
