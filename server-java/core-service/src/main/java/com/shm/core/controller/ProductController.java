package com.shm.core.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.shm.common.model.dto.ProductSearchQuery;
import com.shm.common.model.dto.product.PublishProductRequest;
import com.shm.common.model.dto.product.UpdateProductRequest;
import com.shm.common.util.ResponseBuilder;
import com.shm.core.security.CurrentUser;
import com.shm.core.security.UserPrincipal;
import com.shm.core.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 商品控制器（与 Node.js controllers/product.js 行为完全一致）
 *
 * <p>只做参数提取和响应组装，所有业务逻辑在 ProductService 中。
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * GET /api/products — 商品列表（高频接口）
     */
    @SentinelResource(value = "product-list",
            blockHandler = "handleBlock", blockHandlerClass = com.shm.common.sentinel.GlobalBlockHandler.class,
            fallback = "handleFallback", fallbackClass = com.shm.common.sentinel.GlobalFallback.class)
    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int pageSize,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(required = false) String condition,
                                     @RequestParam(required = false) BigDecimal priceMin,
                                     @RequestParam(required = false) BigDecimal priceMax,
                                     @RequestParam(required = false) String sort) {
        pageSize = Math.min(pageSize, 50);
        ProductSearchQuery query = ProductSearchQuery.builder()
                .keyword(keyword)
                .category(category)
                .condition(condition)
                .priceMin(priceMin)
                .priceMax(priceMax)
                .sort(sort)
                .build();
        return ResponseBuilder.ok(productService.list(query, page, pageSize));
    }

    /**
     * GET /api/products/my — 我发布的商品
     */
    @GetMapping("/my")
    public Map<String, Object> my(@CurrentUser UserPrincipal user,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int pageSize,
                                   @RequestParam(required = false) String status) {
        pageSize = Math.min(pageSize, 50);
        return ResponseBuilder.ok(productService.findBySeller(user.getUserId(), status, page, pageSize));
    }

    /**
     * GET /api/products/:id — 商品详情（高频接口）
     */
    @SentinelResource(value = "product-detail",
            blockHandler = "productDetailBlock", blockHandlerClass = com.shm.common.sentinel.GlobalBlockHandler.class,
            fallback = "handleFallback", fallbackClass = com.shm.common.sentinel.GlobalFallback.class)
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id, @CurrentUser UserPrincipal user) {
        return ResponseBuilder.ok(productService.detail(id, user.getUserId(), user.getRole()));
    }

    /**
     * POST /api/products — 发布商品
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@CurrentUser UserPrincipal user,
                                       @Valid @RequestBody PublishProductRequest request) {
        return ResponseBuilder.ok(productService.create(user.getUserId(), user.getCreditScore(), request));
    }

    /**
     * PUT /api/products/:id — 编辑商品
     */
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id,
                                       @CurrentUser UserPrincipal user,
                                       @RequestBody UpdateProductRequest request) {
        return ResponseBuilder.ok(productService.update(id, user.getUserId(), request));
    }

    /**
     * DELETE /api/products/:id — 删除商品（软删除）
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id, @CurrentUser UserPrincipal user) {
        productService.delete(id, user.getUserId());
        return ResponseBuilder.ok(null);
    }
}
