package com.shm.common.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商品图片（对应 product_images 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImage {

    private Long id;
    private Long productId;
    private String url;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
