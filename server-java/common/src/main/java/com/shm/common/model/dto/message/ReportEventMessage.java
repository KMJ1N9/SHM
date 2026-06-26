package com.shm.common.model.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 举报事件消息（发送到 RocketMQ Topic: shm-report-event）
 *
 * <p>由 ReportService.notifyAdmins() / ReportAdminService.pushImMessage() /
 * ProductAdminService.pushImMessage() 发布，ImPushReportConsumer 和
 * ReportNotificationConsumer 分别消费。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportEventMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 举报/工单 ID */
    private Long reportId;

    /** 举报人 ID */
    private Long reporterId;

    /** 被举报人 ID */
    private Long reportedUserId;

    /** 动作: RESOLVED / REJECTED / NEW_REPORT / OFF_SHELF */
    private String action;

    /** 消息标题 */
    private String title;

    /** 消息内容 */
    private String content;

    /** 通知目标用户 ID 列表（IM UserID 字符串） */
    private List<String> targetUids;

    /** 事件时间戳（毫秒） */
    private Long timestamp;
}
