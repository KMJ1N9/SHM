package com.shm.common.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评价实体（对应 reviews 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    private Long id;
    private Long orderId;
    /** 评价人 */
    private Long reviewerId;
    /** 被评价人 */
    private Long revieweeId;
    /** 沟通态度 1-5 */
    private Integer communicationScore;
    /** 守时程度 1-5 */
    private Integer punctualityScore;
    /** 描述一致度 1-5 */
    private Integer accuracyScore;
    private String comment;
    private LocalDateTime createdAt;
}
