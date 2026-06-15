package com.shm.admin.service;

import com.shm.admin.mapper.AnalyticsMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据分析服务（与 Node.js services/analytics.js 行为完全一致）
 *
 * <p>提供管理后台数据统计：概览、热门分类、热门搜索词、数据看板。
 */
@Service
public class AnalyticsService {

    private final AnalyticsMapper analyticsMapper;

    public AnalyticsService(AnalyticsMapper analyticsMapper) {
        this.analyticsMapper = analyticsMapper;
    }

    /**
     * 平台数据概览（与 Node.js analyticsService.overview 一致）
     */
    public Map<String, Object> overview() {
        return Map.of(
                "total_users", analyticsMapper.countActiveUsers(),
                "total_products", analyticsMapper.countActiveProducts(),
                "total_orders", analyticsMapper.countOrders(),
                "pending_reports", analyticsMapper.countPendingReports(),
                "new_users_7d", analyticsMapper.countNewUsers7d(),
                "completed_orders_7d", analyticsMapper.countCompletedOrders7d()
        );
    }

    /**
     * 热门分类（与 Node.js analyticsService.categories 一致）
     */
    public List<Map<String, Object>> categories() {
        return analyticsMapper.hotCategories();
    }

    /**
     * 热门搜索词（与 Node.js analyticsService.searchKeywords 一致）
     */
    public List<Map<String, Object>> searchKeywords() {
        List<Map<String, Object>> rows = analyticsMapper.hotSearchKeywords();
        // 过滤掉 keyword 为 null 的记录（与 Node.js filter(r => r.keyword) 一致）
        return rows.stream()
                .filter(r -> r.get("keyword") != null)
                .collect(Collectors.toList());
    }

    /**
     * 数据看板（与 Node.js analyticsService.dashboard 一致）
     */
    public Map<String, Object> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(overview());
        result.put("hot_categories", categories());
        result.put("hot_keywords", searchKeywords());
        result.put("daily_orders_7d", analyticsMapper.dailyOrders7d());
        return result;
    }
}
