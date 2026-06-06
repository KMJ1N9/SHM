/**
 * 举报/工单数据访问层
 *
 * 封装所有 reports 表的 SQL 操作。
 * 同时负责写入 admin_logs（管理员操作审计）。
 */

const { query } = require('../models/db');

const REPORT_FIELDS = [
  'id', 'reporter_id', 'reported_user_id', 'product_id', 'order_id',
  'type', 'description', 'evidence_images', 'status',
  'resolution', 'deleted_at', 'resolved_at', 'created_at', 'updated_at',
].join(', ');

const reportRepo = {
  /**
   * 创建举报
   * @param {Object} data
   * @returns {Promise<Object>}
   */
  async create(data) {
    const result = await query(
      `INSERT INTO reports
       (reporter_id, reported_user_id, product_id, order_id, type, description, evidence_images)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      [
        data.reporter_id, data.reported_user_id,
        data.product_id || null, data.order_id || null,
        data.type, data.description,
        JSON.stringify(data.evidence_images || []),
      ]
    );
    const [row] = await query(
      `SELECT ${REPORT_FIELDS} FROM reports WHERE id = ?`,
      [result.insertId]
    );
    return row;
  },

  /**
   * 检查同一订单是否已有进行中的举报
   * @param {number} reporterId
   * @param {number} orderId
   * @returns {Promise<boolean>}
   */
  async hasActiveReport(reporterId, orderId) {
    if (!orderId) return false;
    const [row] = await query(
      `SELECT 1 FROM reports
       WHERE reporter_id = ? AND order_id = ? AND status != 'resolved'`,
      [reporterId, orderId]
    );
    return !!row;
  },

  /**
   * 根据 ID 查找举报/工单
   * @param {number} id
   * @returns {Promise<Object|null>}
   */
  async findById(id) {
    const [row] = await query(
      `SELECT ${REPORT_FIELDS} FROM reports WHERE id = ?`,
      [id]
    );
    return row || null;
  },

  /**
   * 更新举报状态
   * @param {number} id
   * @param {string} status
   * @returns {Promise<Object>}
   */
  async updateStatus(id, status) {
    await query(
      'UPDATE reports SET status = ? WHERE id = ?',
      [status, id]
    );
    return this.findById(id);
  },

  /**
   * 裁决工单（写入 resolution + 状态变更）
   * @param {number} id
   * @param {string} resolution
   * @returns {Promise<Object>}
   */
  async resolve(id, resolution) {
    await query(
      `UPDATE reports
       SET status = 'resolved', resolution = ?, resolved_at = NOW()
       WHERE id = ?`,
      [resolution, id]
    );
    return this.findById(id);
  },

  /**
   * 举报/工单列表（客服管理端）
   * @param {Object} filters - { status?, type?, page, pageSize }
   * @returns {Promise<{list: Array, total: number}>}
   */
  async list(filters = {}) {
    const { status = 'pending', type = 'all', page = 1, pageSize = 20 } = filters;
    const conditions = ['r.deleted_at IS NULL'];
    const params = [];

    if (status && status !== 'all') {
      conditions.push('r.status = ?');
      params.push(status);
    }
    if (type && type !== 'all') {
      conditions.push('r.type = ?');
      params.push(type);
    }

    const where = `WHERE ${conditions.join(' AND ')}`;

    const [countResult] = await query(
      `SELECT COUNT(*) AS total FROM reports r ${where}`,
      params
    );

    const offset = (page - 1) * pageSize;
    const rows = await query(
      `SELECT r.${REPORT_FIELDS},
              reporter.nickname AS reporter_nickname, reporter.avatar AS reporter_avatar,
              reported.nickname AS reported_nickname, reported.avatar AS reported_avatar
       FROM reports r
       JOIN users reporter ON r.reporter_id = reporter.id
       JOIN users reported ON r.reported_user_id = reported.id
       ${where}
       ORDER BY r.created_at DESC
       LIMIT ? OFFSET ?`,
      [...params, pageSize, offset]
    );

    return { list: rows, total: countResult.total };
  },

  /**
   * 写入管理员操作审计日志
   * @param {Object} logData - { admin_id, action, target_type, target_id, reason? }
   * @returns {Promise<void>}
   */
  async createAdminLog(logData) {
    await query(
      `INSERT INTO admin_logs (admin_id, action, target_type, target_id, reason)
       VALUES (?, ?, ?, ?, ?)`,
      [
        logData.admin_id, logData.action, logData.target_type,
        logData.target_id, logData.reason || null,
      ]
    );
  },

  /**
   * 查询操作审计日志（管理后台）
   * @param {Object} filters
   * @returns {Promise<{list: Array, total: number}>}
   */
  async listAdminLogs(filters = {}) {
    const { admin_id, target_type, target_id, action, start_date, end_date, page = 1, pageSize = 20 } = filters;
    const conditions = [];
    const params = [];

    if (admin_id) { conditions.push('al.admin_id = ?'); params.push(admin_id); }
    if (target_type) { conditions.push('al.target_type = ?'); params.push(target_type); }
    if (target_id) { conditions.push('al.target_id = ?'); params.push(target_id); }
    if (action) { conditions.push('al.action = ?'); params.push(action); }
    if (start_date) { conditions.push('al.created_at >= ?'); params.push(start_date); }
    if (end_date) { conditions.push('al.created_at <= ?'); params.push(end_date); }

    const where = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';

    const [countResult] = await query(
      `SELECT COUNT(*) AS total FROM admin_logs al ${where}`,
      params
    );

    const offset = (page - 1) * pageSize;
    const rows = await query(
      `SELECT al.id, al.admin_id, al.action, al.target_type, al.target_id,
              al.reason, al.created_at,
              u.phone AS admin_phone
       FROM admin_logs al
       JOIN users u ON al.admin_id = u.id
       ${where}
       ORDER BY al.created_at DESC
       LIMIT ? OFFSET ?`,
      [...params, pageSize, offset]
    );

    return { list: rows, total: countResult.total };
  },
};

module.exports = reportRepo;
