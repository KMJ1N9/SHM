package com.shm.core.mq;

import com.shm.common.model.entity.FailedSystemMessage;
import com.shm.core.mapper.FailedMessageMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 失败消息重试定时任务
 *
 * <p>每 5 分钟扫描 failed_system_messages 表中 status='FAILED' 的记录，
 * 尝试重新发送到 RocketMQ。超过 max_retries 后标记为 PERMANENT_FAILED。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
public class MessageRetryTask {

    private static final Logger log = LoggerFactory.getLogger(MessageRetryTask.class);
    private static final int BATCH_SIZE = 100;

    private final FailedMessageMapper failedMessageMapper;
    private final RocketMQTemplate rocketMQTemplate;

    public MessageRetryTask(FailedMessageMapper failedMessageMapper,
                            RocketMQTemplate rocketMQTemplate) {
        this.failedMessageMapper = failedMessageMapper;
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Scheduled(fixedDelay = 300_000) // 5 minutes
    public void retryFailedMessages() {
        log.debug("开始重试失败消息...");
        List<FailedSystemMessage> failedMessages =
                failedMessageMapper.selectForRetry("FAILED", BATCH_SIZE);
        if (failedMessages.isEmpty()) {
            return;
        }

        log.info("发现 {} 条待重试消息", failedMessages.size());
        for (FailedSystemMessage msg : failedMessages) {
            try {
                rocketMQTemplate.syncSend("shm-order-event", msg.getPayload());
                msg.setStatus("SENT");
                msg.setLastError(null);
                log.info("消息重试成功: failedMsgId={}", msg.getId());
            } catch (Exception e) {
                msg.setRetryCount(msg.getRetryCount() + 1);
                msg.setLastError(e.getMessage());
                if (msg.getRetryCount() >= msg.getMaxRetries()) {
                    msg.setStatus("PERMANENT_FAILED");
                    log.warn("消息永久失败: failedMsgId={}, retries={}",
                            msg.getId(), msg.getRetryCount());
                } else {
                    msg.setStatus("FAILED");
                    log.warn("消息重试失败: failedMsgId={}, retry={}/{}, error={}",
                            msg.getId(), msg.getRetryCount(), msg.getMaxRetries(), e.getMessage());
                }
            }
            failedMessageMapper.updateRetry(msg);
        }
    }
}
