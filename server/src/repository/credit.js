/**
 * 信誉分数据访问层
 *
 * 信誉分存储在 users.credit_score 字段。
 * 信誉分变动记录存储在 notifications 表（type = 'credit_change'）。
 */

const { query } = require('../models/db');

const creditRepo = {
  /**
   * 查询用户信誉分
   * @param {number} userId
   * @returns {Promise<{score: number, userId: number}>}
   */
  async findByUserId(userId) {
    const [row] = await query(
      'SELECT id AS user_id, credit_score AS score FROM users WHERE id = ?',
      [userId]
    );
    return row || null;
  },

  /**
   * 查询信誉分变动记录（从 notifications 表）
   * @param {number} userId
   * @param {Object} pagination - { page, pageSize }
   * @returns {Promise<{list: Array, total: number}>}
   */
  async findChangeLogs(userId, pagination = {}) {
    // query string 参数均为字符串，LIMIT/OFFSET 需要整数
    const page = Math.max(1, parseInt(pagination.page, 10) || 1);
    const pageSize = Math.min(50, Math.max(1, parseInt(pagination.pageSize, 10) || 20));

    const [countResult] = await query(
      `SELECT COUNT(*) AS total FROM notifications
       WHERE user_id = ? AND type = 'credit_change'`,
      [userId]
    );

    const offset = (page - 1) * pageSize;
    const rows = await query(
      `SELECT id, type, title, content, metadata, created_at
       FROM notifications
       WHERE user_id = ? AND type = 'credit_change'
       ORDER BY created_at DESC
       LIMIT ? OFFSET ?`,
      [userId, pageSize, offset]
    );

    return { list: rows, total: countResult.total };
  },

  /**
   * 写入信誉分变动通知
   * @param {Object} data - { userId, delta, reason, currentScore, previousScore, refId? }
   * @returns {Promise<Object>}
   */
  async createChangeLog(data) {
    const { userId, delta, reason, currentScore, previousScore, refId } = data;

    const sign = delta >= 0 ? '+' : '';
    const title = '信誉分变动';
    const content = `你的信誉分变更为：${currentScore}（${sign}${delta} ${reason}）`;

    const metadata = {
      delta,
      reason,
      previous_score: previousScore,
      current_score: currentScore,
    };
    if (refId) {
      metadata.ref_id = refId;
    }

    const result = await query(
      `INSERT INTO notifications (user_id, type, title, content, metadata)
       VALUES (?, 'credit_change', ?, ?, ?)`,
      [userId, title, content, JSON.stringify(metadata)]
    );

    const [row] = await query(
      `SELECT id, type, title, content, metadata, created_at
       FROM notifications WHERE id = ?`,
      [result.insertId]
    );
    return row;
  },
};

module.exports = creditRepo;
