package com.shm.common.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 举报实体（对应 reports 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    private Long id;
    private Long reporterId;
    private Long reportedUserId;
    private Long productId;
    private Long orderId;
    /** 举报类型：描述不符 | 辱骂骚扰 | 疑似骗子 | 其他 */
    private String type;
    private String description;
    /** JSON 数组，证据图片 URL */
    private String evidenceImages;
    private String status;
    private String resolution;
    /** 软删除时间戳 */
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
}
