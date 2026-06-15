package com.shm.common.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理日志归档实体（对应 admin_logs_archive 表）
 * <p>
 * 6 个月以上数据从 admin_logs 迁移至此。不设外键约束——归档数据为历史快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLogArchive {

    private Long id;
    private Long adminId;
    private String action;
    private String targetType;
    private Long targetId;
    private String reason;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
}
