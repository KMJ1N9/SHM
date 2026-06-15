package com.shm.core.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.shm.common.model.dto.order.CreateOrderRequest;
import com.shm.common.util.ResponseBuilder;
import com.shm.core.security.CurrentUser;
import com.shm.core.security.UserPrincipal;
import com.shm.core.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 订单控制器（与 Node.js controllers/order.js 行为完全一致）
 *
 * <p>只做参数提取和响应组装，所有业务逻辑在 OrderService 中。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * POST /api/orders — 创建订单（高频写入接口）
     */
    @SentinelResource(value = "order-create")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@CurrentUser UserPrincipal user,
                                      @Valid @RequestBody CreateOrderRequest request) {
        return ResponseBuilder.ok(orderService.create(user.getUserId(), user.getCreditScore(), request));
    }

    /**
     * GET /api/orders — 我的订单列表（高频读取接口）
     */
    @SentinelResource(value = "order-list")
    @GetMapping
    public Map<String, Object> list(@CurrentUser UserPrincipal user,
                                    @RequestParam(defaultValue = "both") String role,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        pageSize = Math.min(pageSize, 50);
        return ResponseBuilder.ok(orderService.list(user.getUserId(), role, status, page, pageSize));
    }

    /**
     * GET /api/orders/:id — 订单详情
     */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id, @CurrentUser UserPrincipal user) {
        return ResponseBuilder.ok(orderService.detail(id, user.getUserId()));
    }

    /**
     * PUT /api/orders/:id/met — 标记面交
     */
    @PutMapping("/{id}/met")
    public Map<String, Object> markAsMet(@PathVariable Long id, @CurrentUser UserPrincipal user) {
        return ResponseBuilder.ok(orderService.markAsMet(id, user.getUserId()));
    }

    /**
     * PUT /api/orders/:id/confirm — 确认收货
     */
    @PutMapping("/{id}/confirm")
    public Map<String, Object> confirm(@PathVariable Long id, @CurrentUser UserPrincipal user) {
        return ResponseBuilder.ok(orderService.confirm(id, user.getUserId()));
    }

    /**
     * PUT /api/orders/:id/cancel — 取消订单
     */
    @PutMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable Long id, @CurrentUser UserPrincipal user) {
        return ResponseBuilder.ok(orderService.cancel(id, user.getUserId()));
    }
}
