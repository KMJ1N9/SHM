package com.shm.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商品搜索查询参数 DTO（避免 Mapper 方法参数超过 5 个）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchQuery {

    /** 全文搜索关键词（FULLTEXT MATCH） */
    private String keyword;

    /** 商品分类（精确匹配） */
    private String category;

    /** 成色（精确匹配） */
    private String condition;

    /** 最低价格 */
    private BigDecimal priceMin;

    /** 最高价格 */
    private BigDecimal priceMax;

    /** 排序方式：price_asc | price_desc | 默认 created_at DESC */
    private String sort;
}
