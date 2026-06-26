package com.shm.admin.mq;

import com.shm.common.model.dto.message.ReportEventMessage;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 举报事件发布者（admin-service 侧）
 *
 * <p>由 ReportAdminService.pushImMessage() / ProductAdminService.pushImMessage() 使用，
 * 将举报处理结果和商品下架通知异步发送到 RocketMQ。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
public class ReportEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReportEventPublisher.class);

    private static final String REPORT_EVENT_TOPIC = "shm-report-event";

    private final RocketMQTemplate rocketMQTemplate;

    public ReportEventPublisher(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 发送举报事件到 shm-report-event 主题
     *
     * @param message 举报事件消息
     */
    public void publishReportEvent(ReportEventMessage message) {
        try {
            SendResult result = rocketMQTemplate.syncSend(REPORT_EVENT_TOPIC, message);
            log.info("举报事件已发送: reportId={}, action={}, msgId={}",
                    message.getReportId(), message.getAction(), result.getMsgId());
        } catch (Exception e) {
            log.error("举报事件发送失败: reportId={}, error={}",
                    message.getReportId(), e.getMessage());
        }
    }
}
