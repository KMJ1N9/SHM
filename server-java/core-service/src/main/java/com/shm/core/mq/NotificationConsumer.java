package com.shm.core.mq;

import com.shm.common.model.dto.message.OrderEventMessage;
import com.shm.common.model.entity.Notification;
import com.shm.core.repository.NotificationRepository;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 通知消费者 — 消费 shm-order-event 主题，写入站内通知表
 *
 * <p>作为 OrderService.notifyUser() 已写入 DB 后的补充通知路径，
 * 为需要感知订单事件的第三方（如管理员）写入额外通知。
 */
@Service
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "shm-order-event",
        consumerGroup = "${rocketmq.consumer.group:shm-core-consumer}",
        selectorExpression = "*"
)
public class NotificationConsumer implements RocketMQListener<OrderEventMessage> {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationRepository notificationRepository;

    public NotificationConsumer(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void onMessage(OrderEventMessage message) {
        log.info("收到订单事件: orderId={}, type={}", message.getOrderId(), message.getType());
        try {
            Notification notification = Notification.builder()
                    .userId(Long.valueOf(message.getTargetUid()))
                    .type("order_update")
                    .title(message.getTitle())
                    .content(message.getContent())
                    .isRead(false)
                    .metadata("{\"order_id\":" + message.getOrderId() + "}")
                    .build();
            notificationRepository.insert(notification);
            log.info("站内通知已写入: userId={}, orderId={}",
                    message.getTargetUid(), message.getOrderId());
        } catch (Exception e) {
            log.error("站内通知写入失败: orderId={}", message.getOrderId(), e);
            throw new RuntimeException("通知写入失败", e);
        }
    }
}
