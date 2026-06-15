package com.shm.common.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * IM 系统消息失败重试实体（对应 failed_system_messages 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedSystemMessage {

    private Long id;
    /** 消息类型：order_update | review_remind | report_result | credit_change */
    private String messageType;
    /** 目标 IM UserID（IM SDK 用户标识，字符串格式） */
    private String targetUid;
    /** JSON，完整消息体 */
    private String payload;
    private Integer retryCount;
    private Integer maxRetries;
    /** 最近一次失败原因 */
    private String lastError;
    /** 状态：pending | retrying | permanent_failed | sent */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
