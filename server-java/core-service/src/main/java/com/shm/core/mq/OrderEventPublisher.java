package com.shm.core.mq;

import com.shm.common.model.dto.message.OrderEventMessage;
import com.shm.common.model.dto.message.ReportEventMessage;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 订单/举报事件发布者（core-service 侧）
 *
 * <p>将订单状态变更和举报创建事件异步发送到 RocketMQ，
 * 由 NotificationConsumer（写 DB）和 ImPushConsumer（推 IM）分别消费。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private static final String ORDER_EVENT_TOPIC = "shm-order-event";
    private static final String REPORT_EVENT_TOPIC = "shm-report-event";

    private final RocketMQTemplate rocketMQTemplate;

    public OrderEventPublisher(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 发送订单事件到 shm-order-event 主题
     *
     * @param message 订单事件消息
     */
    public void publishOrderEvent(OrderEventMessage message) {
        try {
            SendResult result = rocketMQTemplate.syncSend(ORDER_EVENT_TOPIC, message);
            log.info("订单事件已发送: orderId={}, type={}, msgId={}",
                    message.getOrderId(), message.getType(), result.getMsgId());
        } catch (Exception e) {
            log.error("订单事件发送失败: orderId={}, error={}",
                    message.getOrderId(), e.getMessage());
        }
    }

    /**
     * 发送举报事件到 shm-report-event 主题（core-service 侧，
     * ReportService.notifyAdmins 使用）
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
