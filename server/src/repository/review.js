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
    const { page = 1, pageSize = 20 } = pagination;

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
};

module.exports = reviewRepo;
