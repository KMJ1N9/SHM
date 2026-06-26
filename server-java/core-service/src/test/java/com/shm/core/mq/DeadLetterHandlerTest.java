package com.shm.core.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.common.model.entity.FailedSystemMessage;
import com.shm.core.mapper.FailedMessageMapper;
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
 * DeadLetterHandler 单元测试（Phase 14）
 *
 * <p>验证死信消息被正确持久化到 failed_system_messages 表，写入失败不向上抛出。
 */
@ExtendWith(MockitoExtension.class)
class DeadLetterHandlerTest {

    @Mock
    private FailedMessageMapper failedMessageMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DeadLetterHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DeadLetterHandler(failedMessageMapper, objectMapper);
    }

    @Test
    void onMessage_shouldInsertFailedMessage() {
        String rawMessage = "{\"orderId\":1,\"type\":\"CONFIRMED\",\"content\":\"test\"}";

        handler.onMessage(rawMessage);

        ArgumentCaptor<FailedSystemMessage> captor = ArgumentCaptor.forClass(FailedSystemMessage.class);
        verify(failedMessageMapper).insert(captor.capture());

        FailedSystemMessage failed = captor.getValue();
        assertEquals("rocketmq_dlq", failed.getMessageType());
        assertEquals("system", failed.getTargetUid());
        assertEquals(rawMessage, failed.getPayload());
        assertEquals(0, failed.getRetryCount());
        assertEquals(5, failed.getMaxRetries());
        assertEquals("FAILED", failed.getStatus());
    }

    @Test
    void onMessage_insertFail_shouldNotThrow() {
        String rawMessage = "invalid message";
        doThrow(new RuntimeException("DB error"))
                .when(failedMessageMapper).insert(any());

        // 不应抛出异常，避免死信处理自身导致死循环
        handler.onMessage(rawMessage);

        verify(failedMessageMapper).insert(any());
    }
}
