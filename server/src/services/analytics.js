/**
 * 数据分析服务
 *
 * 提供管理后台数据统计：概览、热门分类、热门搜索词、数据看板。
 *
 * 数据来源：各业务表聚合查询 + user_events 埋点表。
 */

const { query } = require('../models/db');

const analyticsService = {
  /**
   * 平台数据概览
   * @returns {Promise<Object>}
   */
  async overview() {
    const [[userCount], [productCount], [orderCount], [reportCount]] = await Promise.all([
      query('SELECT COUNT(*) AS total FROM users WHERE status = ?', ['active']),
      query('SELECT COUNT(*) AS total FROM products WHERE status = ?', ['active']),
      query('SELECT COUNT(*) AS total FROM orders'),
      query('SELECT COUNT(*) AS total FROM reports WHERE status = ?', ['pending']),
    ]);

    // 近 7 天新增用户
    const [newUsers] = await query(
      `SELECT COUNT(*) AS total FROM users
       WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)`
    );

    // 近 7 天交易完成数
    const [completedOrders] = await query(
      `SELECT COUNT(*) AS total FROM orders
       WHERE status = 'completed' AND confirmed_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)`
    );

    return {
      total_users: userCount.total,
      total_products: productCount.total,
      total_orders: orderCount.total,
      pending_reports: reportCount.total,
      new_users_7d: newUsers.total,
      completed_orders_7d: completedOrders.total,
    };
  },

  /**
   * 热门分类（按商品数量统计）
   * @returns {Promise<Array>}
   */
  async categories() {
    const rows = await query(
      `SELECT category, COUNT(*) AS count
       FROM products
       WHERE status = 'active'
       GROUP BY category
       ORDER BY count DESC
       LIMIT 10`
    );
    return rows;
  },

  /**
   * 热门搜索词（从 user_events 埋点聚合）
   * @returns {Promise<Array>}
   */
  async searchKeywords() {
    const rows = await query(
      `SELECT JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.keyword')) AS keyword,
              COUNT(*) AS count
       FROM user_events
       WHERE event = 'search' AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
       GROUP BY keyword
       ORDER BY count DESC
       LIMIT 20`
    );
    return rows.filter(r => r.keyword);
  },

  /**
   * 管理后台数据看板
   * @returns {Promise<Object>}
   */
  async dashboard() {
    const overview = await this.overview();
    const categories = await this.categories();
    const keywords = await this.searchKeywords();

    // 近 7 天每日新增订单
    const dailyOrders = await query(
      `SELECT DATE(created_at) AS date, COUNT(*) AS count
       FROM orders
       WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
       GROUP BY DATE(created_at)
       ORDER BY date ASC`
    );

    return {
      ...overview,
      hot_categories: categories,
      hot_keywords: keywords,
      daily_orders_7d: dailyOrders,
    };
  },
};

module.exports = analyticsService;
