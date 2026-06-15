package com.shm.common.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 举报证据图片（对应 report_evidence 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportEvidence {

    private Long id;
    private Long reportId;
    private String url;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
