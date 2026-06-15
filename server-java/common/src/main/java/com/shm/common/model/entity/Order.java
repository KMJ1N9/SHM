package com.shm.common.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 订单实体（对应 orders 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private Long id;
    private Long productId;
    private Long buyerId;
    private Long sellerId;
    private String status;
    /** 取消方：buyer / seller */
    private String cancelledBy;
    /** ${buyer_id}_${product_id}，防重复提交 */
    private String idempotentKey;
    /** JSON，交易时商品快照 */
    private String productSnapshot;
    /** 任一方点击"已面交"的时间 */
    private LocalDateTime metAt;
    /** 确认收货时间 */
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
