package com.shm.common.model.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 发布商品请求（与 Node.js productService.create 参数一致）
 *
 * <p>前端发送 snake_case JSON，JacksonConfig 反序列化默认驼峰，需显式映射。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishProductRequest {

    @NotBlank(message = "商品标题不能为空")
    private String title;

    private String description;

    @NotBlank(message = "请选择商品分类")
    private String category;

    @NotBlank(message = "请选择商品成色")
    private String condition;

    @NotNull(message = "请输入原价")
    @DecimalMin(value = "0.01", message = "原价必须大于 0")
    @JsonProperty("original_price")
    private BigDecimal originalPrice;

    @NotNull(message = "请输入售价")
    @DecimalMin(value = "0.01", message = "售价必须大于 0")
    private BigDecimal price;

    @NotBlank(message = "请输入交易地点")
    @JsonProperty("trade_location")
    private String tradeLocation;

    /** 是否接受议价，默认 true */
    private Boolean negotiable;

    /** 图片 URL 列表（1-6 张） */
    @Size(min = 1, max = 6, message = "图片数量为 1-6 张")
    private List<String> images;
}
