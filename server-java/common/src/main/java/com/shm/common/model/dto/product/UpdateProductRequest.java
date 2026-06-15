package com.shm.common.model.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 编辑商品请求（所有字段可选，与 Node.js productService.update 一致）
 *
 * <p>前端发送 snake_case JSON，JacksonConfig 反序列化默认驼峰，需显式映射。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {

    private String title;
    private String description;
    private String category;
    private String condition;

    @JsonProperty("original_price")
    private BigDecimal originalPrice;

    private BigDecimal price;

    @JsonProperty("trade_location")
    private String tradeLocation;

    private Boolean negotiable;
    private List<String> images;
}
