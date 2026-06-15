package com.shm.admin.service;

import com.shm.admin.mapper.AnalyticsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AnalyticsService 单元测试（Phase 11 11.1.6）
 *
 * <p>覆盖数据概览/热门分类/热门搜索词/数据看板，Mock AnalyticsMapper 层。
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private AnalyticsMapper analyticsMapper;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(analyticsMapper);
    }

    // ============================================================
    // overview — 平台数据概览
    // ============================================================

    @Test
    void overview_shouldReturnAllCounts() {
        when(analyticsMapper.countActiveUsers()).thenReturn(1000L);
        when(analyticsMapper.countActiveProducts()).thenReturn(500L);
        when(analyticsMapper.countOrders()).thenReturn(300L);
        when(analyticsMapper.countPendingReports()).thenReturn(10L);
        when(analyticsMapper.countNewUsers7d()).thenReturn(50L);
        when(analyticsMapper.countCompletedOrders7d()).thenReturn(25L);

        Map<String, Object> result = analyticsService.overview();

        assertEquals(1000L, result.get("total_users"));
        assertEquals(500L, result.get("total_products"));
        assertEquals(300L, result.get("total_orders"));
        assertEquals(10L, result.get("pending_reports"));
        assertEquals(50L, result.get("new_users_7d"));
        assertEquals(25L, result.get("completed_orders_7d"));
    }

    @Test
    void overview_zeroCounts_shouldReturnZeros() {
        when(analyticsMapper.countActiveUsers()).thenReturn(0L);
        when(analyticsMapper.countActiveProducts()).thenReturn(0L);
        when(analyticsMapper.countOrders()).thenReturn(0L);
        when(analyticsMapper.countPendingReports()).thenReturn(0L);
        when(analyticsMapper.countNewUsers7d()).thenReturn(0L);
        when(analyticsMapper.countCompletedOrders7d()).thenReturn(0L);

        Map<String, Object> result = analyticsService.overview();

        assertEquals(0L, result.get("total_users"));
        assertEquals(0L, result.get("pending_reports"));
    }

    // ============================================================
    // categories — 热门分类
    // ============================================================

    @Test
    void categories_shouldReturnList() {
        List<Map<String, Object>> cats = List.of(
                Map.of("category", "电子产品", "count", 50L),
                Map.of("category", "教材教辅", "count", 30L)
        );
        when(analyticsMapper.hotCategories()).thenReturn(cats);

        List<Map<String, Object>> result = analyticsService.categories();

        assertEquals(2, result.size());
        assertEquals("电子产品", result.get(0).get("category"));
    }

    @Test
    void categories_empty_shouldReturnEmptyList() {
        when(analyticsMapper.hotCategories()).thenReturn(List.of());

        List<Map<String, Object>> result = analyticsService.categories();

        assertTrue(result.isEmpty());
    }

    // ============================================================
    // searchKeywords — 热门搜索词
    // ============================================================

    @Test
    void searchKeywords_shouldFilterNullKeywords() {
        // Map.of() 不允许 null 值，使用 LinkedHashMap
        Map<String, Object> row1 = new java.util.LinkedHashMap<>();
        row1.put("keyword", "iPhone");
        row1.put("count", 10L);
        Map<String, Object> row2 = new java.util.LinkedHashMap<>();
        row2.put("keyword", null);  // 应被过滤
        row2.put("count", 5L);
        Map<String, Object> row3 = new java.util.LinkedHashMap<>();
        row3.put("keyword", "MacBook");
        row3.put("count", 3L);
        List<Map<String, Object>> rows = List.of(row1, row2, row3);
        when(analyticsMapper.hotSearchKeywords()).thenReturn(rows);

        List<Map<String, Object>> result = analyticsService.searchKeywords();

        assertEquals(2, result.size());
        assertEquals("iPhone", result.get(0).get("keyword"));
        assertEquals("MacBook", result.get(1).get("keyword"));
    }

    @Test
    void searchKeywords_allNull_shouldReturnEmptyList() {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("keyword", null);
        row.put("count", 5L);
        List<Map<String, Object>> rows = List.of(row);
        when(analyticsMapper.hotSearchKeywords()).thenReturn(rows);

        List<Map<String, Object>> result = analyticsService.searchKeywords();

        assertTrue(result.isEmpty());
    }

    // ============================================================
    // dashboard — 数据看板
    // ============================================================

    @Test
    void dashboard_shouldAggregateAllData() {
        when(analyticsMapper.countActiveUsers()).thenReturn(100L);
        when(analyticsMapper.countActiveProducts()).thenReturn(50L);
        when(analyticsMapper.countOrders()).thenReturn(30L);
        when(analyticsMapper.countPendingReports()).thenReturn(5L);
        when(analyticsMapper.countNewUsers7d()).thenReturn(10L);
        when(analyticsMapper.countCompletedOrders7d()).thenReturn(8L);
        when(analyticsMapper.hotCategories()).thenReturn(List.of(Map.of("category", "电子产品")));
        when(analyticsMapper.hotSearchKeywords()).thenReturn(List.of(Map.of("keyword", "iPhone")));
        when(analyticsMapper.dailyOrders7d()).thenReturn(List.of(Map.of("date", "2026-06-14", "count", 5)));

        Map<String, Object> result = analyticsService.dashboard();

        assertEquals(100L, result.get("total_users"));
        assertNotNull(result.get("hot_categories"));
        assertNotNull(result.get("hot_keywords"));
        assertNotNull(result.get("daily_orders_7d"));
    }

    @Test
    void dashboard_dailyOrders7d_shouldBeIncluded() {
        when(analyticsMapper.countActiveUsers()).thenReturn(0L);
        when(analyticsMapper.countActiveProducts()).thenReturn(0L);
        when(analyticsMapper.countOrders()).thenReturn(0L);
        when(analyticsMapper.countPendingReports()).thenReturn(0L);
        when(analyticsMapper.countNewUsers7d()).thenReturn(0L);
        when(analyticsMapper.countCompletedOrders7d()).thenReturn(0L);
        when(analyticsMapper.hotCategories()).thenReturn(List.of());
        when(analyticsMapper.hotSearchKeywords()).thenReturn(List.of());

        List<Map<String, Object>> dailyOrders = List.of(
                Map.of("date", "2026-06-14", "count", 5L)
        );
        when(analyticsMapper.dailyOrders7d()).thenReturn(dailyOrders);

        Map<String, Object> result = analyticsService.dashboard();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) result.get("daily_orders_7d");
        assertEquals(1, orders.size());
    }
}
