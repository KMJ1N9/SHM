package com.shm.admin.controller;

import com.shm.admin.security.CurrentUser;
import com.shm.admin.security.UserPrincipal;
import com.shm.admin.service.ProductAdminService;
import com.shm.common.util.ResponseBuilder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 商品管理控制器（与 Node.js adminController 商品管理部分行为完全一致）
 *
 * <p>仅管理员可操作。
 */
@RestController
@RequestMapping("/api/admin/products")
public class ProductAdminController {

    private final ProductAdminService productAdminService;

    public ProductAdminController(ProductAdminService productAdminService) {
        this.productAdminService = productAdminService;
    }

    /**
     * GET /api/admin/products — 管理端商品列表（含全部状态）
     */
    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int pageSize) {
        pageSize = Math.min(pageSize, 50);
        return ResponseBuilder.ok(productAdminService.listAllProducts(status, keyword, page, pageSize));
    }

    /**
     * PUT /api/admin/products/:id/off-shelf — 下架商品
     */
    @PutMapping("/{id}/off-shelf")
    public Map<String, Object> offShelf(@PathVariable Long id,
                                         @CurrentUser UserPrincipal user) {
        return ResponseBuilder.ok(productAdminService.offShelfProduct(id, user.getUserId()));
    }
}
