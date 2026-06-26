package com.shm.common.model.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通用通知消息（IM 推送专用，消费者转换后调用 TencentImService）
 *
 * <p>预留 — 后续如果需要统一消息格式，Publisher 可将具体事件转换为本格式再发送。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标用户 IM UserID */
    private String targetUid;

    /** 消息类型: order_update | report_result | credit_change | ban */
    private String messageType;

    /** 消息标题 */
    private String title;

    /** 消息内容 */
    private String content;

    /** 扩展字段（JSON 字符串: order_id, report_id, route 等） */
    private String payload;
}
