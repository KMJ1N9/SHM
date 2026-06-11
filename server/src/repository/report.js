/**
 * 举报/工单数据访问层
 *
 * 封装所有 reports 表的 SQL 操作。
 * 同时负责写入 admin_logs（管理员操作审计）。
 */

const { query, transaction } = require('../models/db');
const { notFound } = require('../utils/errors');

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
   * 根据 ID 查找举报/工单详情（含举报人/被举报人信息）
   * @param {number} id
   * @returns {Promise<Object|null>}
   */
  async findDetailById(id) {
    const [row] = await query(
      `SELECT r.id, r.reporter_id, r.reported_user_id, r.product_id, r.order_id,
              r.type, r.description, r.evidence_images, r.status,
              r.resolution, r.deleted_at, r.resolved_at, r.created_at, r.updated_at,
              reporter.nickname AS reporter_nickname, reporter.avatar AS reporter_avatar,
              reported.nickname AS reported_nickname, reported.avatar AS reported_avatar
       FROM reports r
       JOIN users reporter ON r.reporter_id = reporter.id
       JOIN users reported ON r.reported_user_id = reported.id
       WHERE r.id = ?`,
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
   * 裁决工单并扣减信誉分（事务：工单裁决 + 信誉分扣减 + 变动通知，原子操作）
   *
   * 使用 FOR UPDATE 锁定用户行，确保并发场景下信誉分计算正确。
   *
   * @param {Object} params
   * @param {number} params.ticketId       - 工单 ID
   * @param {string} params.resolution     - 裁决结果
   * @param {number} params.reportedUserId - 被举报人 ID
   * @param {number} params.deductCredit   - 扣减分值（0 表示不扣分）
   * @param {number} params.creditMax      - 信誉分上限
   * @returns {Promise<{ticket: Object, newCreditScore?: number}>}
   */
  async resolveWithPenalty({ ticketId, resolution, reportedUserId, deductCredit, creditMax }) {
    return transaction(async (conn) => {
      // 1. 裁决工单
      await conn.execute(
        `UPDATE reports
         SET status = 'resolved', resolution = ?, resolved_at = NOW()
         WHERE id = ?`,
        [resolution, ticketId]
      );

      const [[ticket]] = await conn.execute(
        `SELECT ${REPORT_FIELDS} FROM reports WHERE id = ?`,
        [ticketId]
      );

      // 2. 扣减信誉分（原子读-改-写，FOR UPDATE 防并发）
      let newCreditScore;
      if (deductCredit && deductCredit > 0) {
        const [[user]] = await conn.execute(
          'SELECT credit_score FROM users WHERE id = ? FOR UPDATE',
          [reportedUserId]
        );
        if (!user) {
          throw notFound('用户');
        }

        const previousScore = user.credit_score;
        newCreditScore = Math.max(0, Math.min(previousScore - deductCredit, creditMax));

        await conn.execute(
          'UPDATE users SET credit_score = ? WHERE id = ?',
          [newCreditScore, reportedUserId]
        );

        // 3. 写入信誉分变动通知（在同一事务内）
        const content = `你的信誉分变更为：${newCreditScore}（-${deductCredit} 举报成立）`;
        const metadata = JSON.stringify({
          delta: -deductCredit,
          reason: '举报成立',
          previous_score: previousScore,
          current_score: newCreditScore,
          ref_id: ticketId,
        });

        await conn.execute(
          `INSERT INTO notifications (user_id, type, title, content, metadata)
           VALUES (?, 'credit_change', '信誉分变动', ?, ?)`,
          [reportedUserId, content, metadata]
        );
      }

      return { ticket, newCreditScore };
    });
  },

  /**
   * 举报/工单列表（客服管理端）
   * @param {Object} filters - { status?, type?, page, pageSize }
   * @returns {Promise<{list: Array, total: number}>}
   */
  async list(filters = {}) {
    const { status = 'pending', type = 'all', reporter_id, page: rawPage, pageSize: rawPageSize } = filters;
    // 强制整数解析（query string 参数均为 string 类型，LIMIT/OFFSET 需要整数）
    const page = Math.max(1, parseInt(rawPage, 10) || 1);
    const pageSize = Math.min(50, Math.max(1, parseInt(rawPageSize, 10) || 20));
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
    // 前端用户只能查看自己的举报；客服管理端不传 reporter_id 查看全部
    if (reporter_id) {
      conditions.push('r.reporter_id = ?');
      params.push(reporter_id);
    }

    const where = `WHERE ${conditions.join(' AND ')}`;

    const [countResult] = await query(
      `SELECT COUNT(*) AS total FROM reports r ${where}`,
      params
    );

    const offset = (page - 1) * pageSize;
    const rows = await query(
      `SELECT r.id, r.reporter_id, r.reported_user_id, r.product_id, r.order_id,
              r.type, r.description, r.evidence_images, r.status,
              r.resolution, r.deleted_at, r.resolved_at, r.created_at, r.updated_at,
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
    const { admin_id, target_type, target_id, action, start_date, end_date, page: rawPage, pageSize: rawPageSize } = filters;
    // 强制整数解析（query string 参数均为 string 类型，LIMIT/OFFSET 需要整数）
    const page = Math.max(1, parseInt(rawPage, 10) || 1);
    const pageSize = Math.min(50, Math.max(1, parseInt(rawPageSize, 10) || 20));
    const conditions = [];
    const params = [];

    if (admin_id) { conditions.push('al.admin_id = ?'); params.push(admin_id); }
    if (target_type) { conditions.push('al.target_type = ?'); params.push(target_type); }
    if (target_id) { conditions.push('al.target_id = ?'); params.push(target_id); }
    if (action) { conditions.push('al.action = ?'); params.push(action); }
    if (start_date) { conditions.push('al.created_at >= ?'); params.push(start_date); }
    // 若 end_date 不含时间分量（如 '2026-06-11'），MySQL 会将其视为 00:00:00，
    // 导致当天所有记录被排除。自动补齐 23:59:59 确保覆盖全天。
    if (end_date) {
      const end = end_date.includes(':') ? end_date : `${end_date} 23:59:59`;
      conditions.push('al.created_at <= ?');
      params.push(end);
    }

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
