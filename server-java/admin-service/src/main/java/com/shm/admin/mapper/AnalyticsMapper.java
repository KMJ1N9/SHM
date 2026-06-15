package com.shm.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 数据分析 Mapper（与 Node.js services/analytics.js 行为完全一致）
 *
 * <p>聚合查询：平台概览 / 热门分类 / 热门搜索词 / 七日趋势。
 */
@Mapper
public interface AnalyticsMapper {

    /** 活跃用户总数 */
    @Select("SELECT COUNT(*) AS total FROM users WHERE status = 'active'")
    long countActiveUsers();

    /** 在售商品总数 */
    @Select("SELECT COUNT(*) AS total FROM products WHERE status = 'active'")
    long countActiveProducts();

    /** 订单总数 */
    @Select("SELECT COUNT(*) AS total FROM orders")
    long countOrders();

    /** 待处理举报数 */
    @Select("SELECT COUNT(*) AS total FROM reports WHERE status = 'pending'")
    long countPendingReports();

    /** 近 7 天新增用户数 */
    @Select("SELECT COUNT(*) AS total FROM users WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)")
    long countNewUsers7d();

    /** 近 7 天完成交易数 */
    @Select("SELECT COUNT(*) AS total FROM orders WHERE status = 'completed' AND confirmed_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)")
    long countCompletedOrders7d();

    /** 热门分类（按在售商品数排序，TOP 10） */
    @Select("SELECT category, COUNT(*) AS count FROM products WHERE status = 'active' GROUP BY category ORDER BY count DESC LIMIT 10")
    List<Map<String, Object>> hotCategories();

    /** 热门搜索词（近 30 天，从 user_events 埋点聚合，TOP 20） */
    @Select("SELECT JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.keyword')) AS keyword, COUNT(*) AS count " +
            "FROM user_events WHERE event = 'search' AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
            "GROUP BY keyword ORDER BY count DESC LIMIT 20")
    List<Map<String, Object>> hotSearchKeywords();

    /** 近 7 天每日新增订单 — 日期以字符串返回（前端 formatDateLabel 需 split） */
    @Select("SELECT DATE_FORMAT(DATE(created_at), '%Y-%m-%d') AS date, COUNT(*) AS count FROM orders "
            + "WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) "
            + "GROUP BY DATE_FORMAT(DATE(created_at), '%Y-%m-%d') ORDER BY date ASC")
    List<Map<String, Object>> dailyOrders7d();
}
