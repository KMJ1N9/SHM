/**
 * 通知服务
 *
 * 处理站内通知的查询、已读、未读数。
 */

const { query } = require('../models/db');
const { notFound } = require('../utils/errors');

const notificationService = {
  /**
   * 我的通知列表
   * @param {number} userId
   * @param {Object} filters - { type?, page, pageSize }
   * @returns {Promise<{list: Array, total: number}>}
   */
  async list(userId, filters = {}) {
    const { type } = filters;
    const page = parseInt(filters.page, 10) || 1;
    const pageSize = Math.min(parseInt(filters.pageSize, 10) || 20, 50);
    const conditions = ['user_id = ?'];
    const params = [userId];

    if (type && type !== 'all') {
      conditions.push('type = ?');
      params.push(type);
    }

    const where = `WHERE ${conditions.join(' AND ')}`;

    const [countResult] = await query(
      `SELECT COUNT(*) AS total FROM notifications ${where}`,
      params
    );

    const offset = (page - 1) * pageSize;
    const rows = await query(
      `SELECT id, type, title, content, is_read, metadata, created_at
       FROM notifications ${where}
       ORDER BY created_at DESC
       LIMIT ? OFFSET ?`,
      [...params, pageSize, offset]
    );

    return { list: rows, total: countResult.total };
  },

  /**
   * 未读通知数
   * @param {number} userId
   * @returns {Promise<{count: number}>}
   */
  async unreadCount(userId) {
    const [row] = await query(
      `SELECT COUNT(*) AS count FROM notifications
       WHERE user_id = ? AND is_read = 0`,
      [userId]
    );
    return { count: row.count };
  },

  /**
   * 标记单条通知为已读
   * @param {number} notificationId
   * @param {number} userId
   * @returns {Promise<void>}
   */
  async read(notificationId, userId) {
    const result = await query(
      'UPDATE notifications SET is_read = 1 WHERE id = ? AND user_id = ?',
      [notificationId, userId]
    );
    if (result.affectedRows === 0) {
      throw notFound('通知');
    }
  },

  /**
   * 全部标记为已读
   * @param {number} userId
   * @returns {Promise<number>} 更新的条数
   */
  async readAll(userId) {
    const result = await query(
      'UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0',
      [userId]
    );
    return { updated: result.affectedRows };
  },
};

module.exports = notificationService;
