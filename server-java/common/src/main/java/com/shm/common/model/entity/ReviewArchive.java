package com.shm.common.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评价归档实体（对应 reviews_archive 表）
 * <p>
 * 1 年以上数据从 reviews 迁移至此。不设外键约束——归档数据为历史快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewArchive {

    private Long id;
    private Long orderId;
    private Long reviewerId;
    private Long revieweeId;
    private Integer communicationScore;
    private Integer punctualityScore;
    private Integer accuracyScore;
    private String comment;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
}
