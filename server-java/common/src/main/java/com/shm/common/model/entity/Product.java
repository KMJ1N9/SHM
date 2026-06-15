package com.shm.common.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体（对应 products 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    private Long id;
    private Long sellerId;
    private String title;
    private String description;
    private String category;
    /** 成色：全新 | 95新 | 9成新 | 8成新 | 7成新及以下 */
    private String condition;
    private BigDecimal originalPrice;
    private BigDecimal price;
    /** 面交地点 */
    private String tradeLocation;
    /** 0=不接受议价 1=接受 */
    private Boolean negotiable;
    /** JSON 数组，存图片 URL */
    private String images;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
