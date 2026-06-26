package com.shm.admin.mq;

import com.shm.admin.mapper.NotificationMapper;
import com.shm.common.model.dto.message.ReportEventMessage;
import com.shm.common.model.entity.Notification;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 举报通知消费者 — 消费 shm-report-event 主题，写入 admin 侧站内通知表
 */
@Service
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "shm-report-event",
        consumerGroup = "${rocketmq.consumer.group:shm-admin-consumer}",
        selectorExpression = "*"
)
public class ReportNotificationConsumer implements RocketMQListener<ReportEventMessage> {

    private static final Logger log = LoggerFactory.getLogger(ReportNotificationConsumer.class);

    private final NotificationMapper notificationMapper;

    public ReportNotificationConsumer(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @Override
    public void onMessage(ReportEventMessage message) {
        log.info("收到举报通知事件: reportId={}, action={}",
                message.getReportId(), message.getAction());

        if (message.getTargetUids() == null || message.getTargetUids().isEmpty()) {
            return;
        }

        for (String uid : message.getTargetUids()) {
            try {
                Notification notification = Notification.builder()
                        .userId(Long.valueOf(uid))
                        .type("report_result")
                        .title(message.getTitle())
                        .content(message.getContent())
                        .isRead(false)
                        .metadata("{\"report_id\":" + message.getReportId() + "}")
                        .build();
                notificationMapper.insert(notification);
            } catch (Exception e) {
                log.error("举报通知写入失败: targetUid={}, reportId={}, error={}",
                        uid, message.getReportId(), e.getMessage());
            }
        }
    }
}
