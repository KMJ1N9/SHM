package com.shm.admin.controller;

import com.shm.admin.service.AnalyticsService;
import com.shm.common.util.ResponseBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据统计控制器（与 Node.js adminController 数据统计部分行为完全一致）
 *
 * <p>提供平台概览、热门分类、热门搜索词、数据看板。
 */
@RestController
@RequestMapping("/api/admin")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * GET /api/admin/analytics/overview — 平台数据概览
     */
    @GetMapping("/analytics/overview")
    public Map<String, Object> overview() {
        return ResponseBuilder.ok(analyticsService.overview());
    }

    /**
     * GET /api/admin/analytics/categories — 热门分类
     */
    @GetMapping("/analytics/categories")
    public Map<String, Object> categories() {
        return ResponseBuilder.ok(analyticsService.categories());
    }

    /**
     * GET /api/admin/analytics/search-keywords — 热门搜索词
     */
    @GetMapping("/analytics/search-keywords")
    public Map<String, Object> searchKeywords() {
        return ResponseBuilder.ok(analyticsService.searchKeywords());
    }

    /**
     * GET /api/admin/dashboard — 数据看板
     */
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return ResponseBuilder.ok(analyticsService.dashboard());
    }
}
