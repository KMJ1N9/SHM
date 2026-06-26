package com.shm.common.model.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 订单事件消息（发送到 RocketMQ Topic: shm-order-event）
 *
 * <p>由 OrderService.notifyUser() 发布，ImPushOrderConsumer 和 NotificationConsumer 分别消费。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEventMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long orderId;

    /** 买家 ID */
    private Long buyerId;

    /** 卖家 ID */
    private Long sellerId;

    /** 事件类型: CONFIRMED / CANCELLED / MET */
    private String type;

    /** 消息标题 */
    private String title;

    /** 消息内容 */
    private String content;

    /** 通知目标用户 ID（IM UserID 字符串） */
    private String targetUid;

    /** 事件时间戳（毫秒） */
    private Long timestamp;
}
