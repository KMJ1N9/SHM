package com.shm.im.mq;

import com.shm.common.model.dto.message.OrderEventMessage;
import com.shm.im.service.TencentImService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单 IM 推送消费者 — 消费 shm-order-event 主题，调用腾讯云 IM 发送系统消息
 */
@Service
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "shm-order-event",
        consumerGroup = "${rocketmq.consumer.group:shm-im-consumer}",
        selectorExpression = "*"
)
public class ImPushOrderConsumer implements RocketMQListener<OrderEventMessage> {

    private static final Logger log = LoggerFactory.getLogger(ImPushOrderConsumer.class);

    private final TencentImService tencentImService;

    public ImPushOrderConsumer(TencentImService tencentImService) {
        this.tencentImService = tencentImService;
    }

    @Override
    public void onMessage(OrderEventMessage message) {
        log.info("收到订单 IM 推送事件: orderId={}, targetUid={}",
                message.getOrderId(), message.getTargetUid());
        try {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("order_id", message.getOrderId());
            tencentImService.sendSystemMessage(
                    message.getTargetUid(), message.getTitle(), message.getContent(), extra);
            log.info("IM 推送成功: targetUid={}, orderId={}",
                    message.getTargetUid(), message.getOrderId());
        } catch (Exception e) {
            log.error("IM 推送失败: targetUid={}, orderId={}, error={}",
                    message.getTargetUid(), message.getOrderId(), e.getMessage());
            throw new RuntimeException("IM 推送失败", e);
        }
    }
}
