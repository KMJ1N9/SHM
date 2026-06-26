package com.shm.core.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.common.model.entity.FailedSystemMessage;
import com.shm.core.mapper.FailedMessageMapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 死信处理器 — 监听 RocketMQ DLQ，将不可消费的消息持久化到 failed_system_messages 表
 *
 * <p>RocketMQ 消费者在重试达到上限后将消息移入 DLQ（Dead Letter Queue）。
 * DLQ 主题名约定为 {@code %DLQ%<consumerGroup>}。
 */
@Service
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "%DLQ%${rocketmq.consumer.group:shm-core-consumer}",
        consumerGroup = "${rocketmq.consumer.group:shm-core-dlq-consumer}",
        selectorExpression = "*"
)
public class DeadLetterHandler implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterHandler.class);

    private final FailedMessageMapper failedMessageMapper;
    private final ObjectMapper objectMapper;

    public DeadLetterHandler(FailedMessageMapper failedMessageMapper, ObjectMapper objectMapper) {
        this.failedMessageMapper = failedMessageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(String rawMessage) {
        log.warn("收到死信消息: {}", rawMessage);
        try {
            FailedSystemMessage failed = FailedSystemMessage.builder()
                    .messageType("rocketmq_dlq")
                    .targetUid("system")
                    .payload(rawMessage)
                    .retryCount(0)
                    .maxRetries(5)
                    .status("FAILED")
                    .lastError("Moved to DLQ after max consumer retries")
                    .build();
            failedMessageMapper.insert(failed);
            log.info("死信消息已写入 failed_system_messages: id={}", failed.getId());
        } catch (Exception e) {
            log.error("死信消息写入失败: error={}", e.getMessage(), e);
        }
    }
}
