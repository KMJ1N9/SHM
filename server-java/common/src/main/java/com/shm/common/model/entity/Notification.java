package com.shm.common.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通知实体（对应 notifications 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    private Long id;
    private Long userId;
    /** 通知类型：order_update | review_remind | report_result | credit_change */
    private String type;
    private String title;
    private String content;
    /** 0=未读 1=已读 */
    private Boolean isRead;
    /** JSON，关联业务 ID */
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
