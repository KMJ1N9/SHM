package com.shm.common.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理员操作审计日志（对应 admin_logs 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLog {

    private Long id;
    private Long adminId;
    /** 操作：ban | unban | off_shelf | resolve */
    private String action;
    /** 目标类型：user | product | ticket */
    private String targetType;
    private Long targetId;
    /** 操作原因 */
    private String reason;
    private LocalDateTime createdAt;
}
