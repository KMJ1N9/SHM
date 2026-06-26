package com.shm.im.mq;

import com.shm.common.model.dto.message.ReportEventMessage;
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
 * 举报 IM 推送消费者 — 消费 shm-report-event 主题，逐条推送给目标用户
 */
@Service
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "shm-report-event",
        consumerGroup = "${rocketmq.consumer.group:shm-im-consumer}",
        selectorExpression = "*"
)
public class ImPushReportConsumer implements RocketMQListener<ReportEventMessage> {

    private static final Logger log = LoggerFactory.getLogger(ImPushReportConsumer.class);

    private final TencentImService tencentImService;

    public ImPushReportConsumer(TencentImService tencentImService) {
        this.tencentImService = tencentImService;
    }

    @Override
    public void onMessage(ReportEventMessage message) {
        log.info("收到举报 IM 推送事件: reportId={}, targetCount={}",
                message.getReportId(),
                message.getTargetUids() != null ? message.getTargetUids().size() : 0);

        if (message.getTargetUids() == null || message.getTargetUids().isEmpty()) {
            log.warn("举报事件无目标用户: reportId={}", message.getReportId());
            return;
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("report_id", message.getReportId());

        for (String targetUid : message.getTargetUids()) {
            try {
                tencentImService.sendSystemMessage(
                        targetUid, message.getTitle(), message.getContent(), extra);
                log.info("IM 推送成功: targetUid={}, reportId={}", targetUid, message.getReportId());
            } catch (Exception e) {
                log.error("IM 推送失败: targetUid={}, reportId={}, error={}",
                        targetUid, message.getReportId(), e.getMessage());
                // 继续推送下一个用户，单个失败不阻塞其他人
            }
        }
    }
}
