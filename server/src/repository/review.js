/**
 * 评价数据访问层
 *
 * 封装所有 reviews 表的 SQL 操作。
 */

const { query } = require('../models/db');

const reviewRepo = {
  /**
   * 创建评价
   * @param {Object} data - { order_id, reviewer_id, reviewee_id, communication_score, punctuality_score, accuracy_score, comment? }
   * @returns {Promise<Object>}
   */
  async create(data) {
    const result = await query(
      `INSERT INTO reviews
       (order_id, reviewer_id, reviewee_id, communication_score, punctuality_score, accuracy_score, comment)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      [
        data.order_id, data.reviewer_id, data.reviewee_id,
        data.communication_score, data.punctuality_score, data.accuracy_score,
        data.comment || null,
      ]
    );
    const [row] = await query(
      `SELECT id, order_id, reviewer_id, reviewee_id,
              communication_score, punctuality_score, accuracy_score, comment, created_at
       FROM reviews WHERE id = ?`,
      [result.insertId]
    );
    return row;
  },

  /**
   * 检查是否已评价（防重复）
   * @param {number} orderId
   * @param {number} reviewerId
   * @param {number} revieweeId
   * @returns {Promise<boolean>}
   */
  async exists(orderId, reviewerId, revieweeId) {
    const [row] = await query(
      `SELECT 1 FROM reviews WHERE order_id = ? AND reviewer_id = ? AND reviewee_id = ?`,
      [orderId, reviewerId, revieweeId]
    );
    return !!row;
  },

  /**
   * 获取某订单的所有评价
   * @param {number} orderId
   * @returns {Promise<Array>}
   */
  async findByOrder(orderId) {
    const rows = await query(
      `SELECT r.id, r.order_id, r.reviewer_id, r.reviewee_id,
              r.communication_score, r.punctuality_score, r.accuracy_score,
              r.comment, r.created_at,
              reviewer.nickname AS reviewer_nickname, reviewer.avatar AS reviewer_avatar
       FROM reviews r
       JOIN users reviewer ON r.reviewer_id = reviewer.id
       WHERE r.order_id = ?`,
      [orderId]
    );
    return rows;
  },

  /**
   * 获取某用户的评价列表（含聚合统计）
   * @param {number} userId - 被评价人 ID
   * @param {Object} pagination - { page, pageSize }
   * @returns {Promise<{summary: Object, list: Array, total: number}>}
   */
  async findByReviewee(userId, pagination = {}) {
    // query string 参数均为字符串，LIMIT/OFFSET 需要整数
    const page = Math.max(1, parseInt(pagination.page, 10) || 1);
    const pageSize = Math.min(50, Math.max(1, parseInt(pagination.pageSize, 10) || 20));

    // 聚合统计
    const [summary] = await query(
      `SELECT
         COUNT(*) AS total,
         ROUND(AVG(communication_score), 1) AS avg_communication,
         ROUND(AVG(punctuality_score), 1) AS avg_punctuality,
         ROUND(AVG(accuracy_score), 1) AS avg_accuracy
       FROM reviews WHERE reviewee_id = ?`,
      [userId]
    );

    // 列表（total 复用上方 summary 的聚合结果）
    const offset = (page - 1) * pageSize;
    const rows = await query(
      `SELECT r.id, r.communication_score, r.punctuality_score, r.accuracy_score,
              r.comment, r.created_at,
              reviewer.id AS reviewer_id, reviewer.nickname AS reviewer_nickname,
              reviewer.avatar AS reviewer_avatar
       FROM reviews r
       JOIN users reviewer ON r.reviewer_id = reviewer.id
       WHERE r.reviewee_id = ?
       ORDER BY r.created_at DESC
       LIMIT ? OFFSET ?`,
      [userId, pageSize, offset]
    );

    return {
      summary: {
        total: summary.total || 0,
        avg_communication: summary.avg_communication || 0,
        avg_punctuality: summary.avg_punctuality || 0,
        avg_accuracy: summary.avg_accuracy || 0,
      },
      list: rows,
      total: summary.total || 0,
    };
  },

  /**
   * 游标分页查询用户评价列表
   *
   * 基于 id DESC 的游标分页，O(1) 定位，避免 OFFSET 大页码退化。
   * 游标值 = 上一页最后一条记录的 id，首页传 null。
   * 聚合统计始终为全量（不含 cursor 条件）。
   *
   * @param {number} userId - 被评价人 ID
   * @param {Object} filters - { cursor?, limit }
   * @returns {Promise<{summary: Object, list: Array, total: number, cursor: number|null, hasMore: boolean}>}
   */
  async listByCursor(userId, filters = {}) {
    const cursor = filters.cursor ? parseInt(filters.cursor, 10) : null;
    const limit = Math.min(50, Math.max(1, parseInt(filters.limit, 10) || 20));

    // 聚合统计（全量，不含 cursor 条件）
    const [summary] = await query(
      `SELECT
         COUNT(*) AS total,
         ROUND(AVG(communication_score), 1) AS avg_communication,
         ROUND(AVG(punctuality_score), 1) AS avg_punctuality,
         ROUND(AVG(accuracy_score), 1) AS avg_accuracy
       FROM reviews WHERE reviewee_id = ?`,
      [userId]
    );

    // 列表（含 cursor 条件）
    const dataConditions = ['r.reviewee_id = ?'];
    const dataParams = [userId];
    if (cursor) {
      dataConditions.push('r.id < ?');
      dataParams.push(cursor);
    }

    const rows = await query(
      `SELECT r.id, r.communication_score, r.punctuality_score, r.accuracy_score,
              r.comment, r.created_at,
              reviewer.id AS reviewer_id, reviewer.nickname AS reviewer_nickname,
              reviewer.avatar AS reviewer_avatar
       FROM reviews r
       JOIN users reviewer ON r.reviewer_id = reviewer.id
       WHERE ${dataConditions.join(' AND ')}
       ORDER BY r.id DESC
       LIMIT ?`,
      [...dataParams, limit + 1]
    );

    const hasMore = rows.length > limit;
    const list = rows.slice(0, limit);
    const nextCursor = list.length > 0 ? list[list.length - 1].id : null;

    return {
      summary: {
        total: summary.total || 0,
        avg_communication: summary.avg_communication || 0,
        avg_punctuality: summary.avg_punctuality || 0,
        avg_accuracy: summary.avg_accuracy || 0,
      },
      list,
      total: summary.total || 0,
      cursor: hasMore ? nextCursor : null,
      hasMore,
    };
  },
};

module.exports = reviewRepo;
