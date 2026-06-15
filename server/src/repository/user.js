/**
 * 用户数据访问层
 *
 * 封装所有 users 表的 SQL 操作。
 * 业务层不接触 SQL——所有数据库查询通过本模块完成。
 *
 * 规则（database-rules）：
 *   - 禁止 SELECT *
 *   - 明确列出所需字段
 *   - 所有查询使用参数化（防 SQL 注入）
 */

const { query, transaction } = require('../models/db');

const USER_FIELDS = [
  'id', 'phone', 'nickname', 'avatar', 'class_name', 'dorm_building',
  'role', 'status', 'token_version', 'credit_score',
  'created_at', 'updated_at',
].join(', ');

const PUBLIC_USER_FIELDS = [
  'id', 'nickname', 'avatar', 'class_name', 'dorm_building',
  'credit_score', 'created_at',
].join(', ');

const userRepo = {
  /**
   * 根据手机号查找用户
   * @param {string} phone
   * @returns {Promise<Object|null>}
   */
  async findByPhone(phone) {
    const [row] = await query(
      `SELECT ${USER_FIELDS} FROM users WHERE phone = ?`,
      [phone]
    );
    return row || null;
  },

  /**
   * 根据 ID 查找用户
   * @param {number} id
   * @returns {Promise<Object|null>}
   */
  async findById(id) {
    const [row] = await query(
      `SELECT ${USER_FIELDS} FROM users WHERE id = ?`,
      [id]
    );
    return row || null;
  },

  /**
   * 根据 ID 查找用户公开信息（个人主页）
   * @param {number} id
   * @returns {Promise<Object|null>}
   */
  async findPublicById(id) {
    const [row] = await query(
      `SELECT ${PUBLIC_USER_FIELDS} FROM users WHERE id = ?`,
      [id]
    );
    return row || null;
  },

  /**
   * 创建新用户（注册）
   * @param {Object} userData - { phone, nickname? }
   * @returns {Promise<Object>} 创建的用户记录
   */
  async create(userData) {
    const result = await query(
      `INSERT INTO users (phone, nickname) VALUES (?, ?)`,
      [userData.phone, userData.nickname || '微信用户']
    );
    return this.findById(result.insertId);
  },

  /**
   * 更新个人资料
   * @param {number} id - 用户 ID
   * @param {Object} updates - 要更新的字段 { nickname?, avatar?, class_name?, dorm_building? }
   * @returns {Promise<Object>} 更新后的用户记录
   */
  async updateProfile(id, updates) {
    const fields = [];
    const params = [];

    for (const [key, value] of Object.entries(updates)) {
      if (value !== undefined && value !== null) {
        fields.push(`${key} = ?`);
        params.push(value);
      }
    }

    if (fields.length === 0) return this.findById(id);

    params.push(id);
    await query(
      `UPDATE users SET ${fields.join(', ')} WHERE id = ?`,
      params
    );
    return this.findById(id);
  },

  /**
   * 更新信誉分（原子操作：score + delta）
   * @param {number} id - 用户 ID
   * @param {number} delta - 变动值（正数加分，负数扣分）
   * @param {number} max - 信誉分上限
   * @returns {Promise<number>} 更新后的信誉分
   */
  async updateCreditScore(id, delta, max = 200) {
    const result = await query(
      `UPDATE users SET credit_score = LEAST(GREATEST(credit_score + ?, 0), ?) WHERE id = ?`,
      [delta, max, id]
    );
    return result;
  },

  /**
   * 封禁/解封用户
   * @param {number} id - 用户 ID
   * @param {'active'|'banned'} status - 目标状态
   * @returns {Promise<Object>}
   */
  async updateStatus(id, status) {
    await query(
      `UPDATE users SET status = ?, token_version = token_version + 1 WHERE id = ?`,
      [status, id]
    );
    return this.findById(id);
  },

  /**
   * 查找客服用户（取第一个 active 状态的 cs 角色用户）
   * @returns {Promise<Object|null>}
   */
  async findCSUser() {
    const [row] = await query(
      `SELECT id, nickname, avatar FROM users WHERE role = 'cs' AND status = 'active' LIMIT 1`
    );
    return row || null;
  },

  /**
   * 查找管理员用户（取第一个 active 状态的 admin 角色用户）
   * @returns {Promise<Object|null>}
   */
  async findAdminUser() {
    const [row] = await query(
      `SELECT id, nickname, avatar FROM users WHERE role = 'admin' AND status = 'active' LIMIT 1`
    );
    return row || null;
  },

  /**
   * 管理后台：用户列表（支持搜索/筛选/分页）
   * @param {Object} filters - { keyword?, status?, role?, page, pageSize }
   * @returns {Promise<{list: Array, total: number}>}
   */
  async listWithFilters(filters) {
    const { keyword, status, role } = filters;
    // query string 参数均为字符串，LIMIT/OFFSET 需要整数
    const page = Math.max(1, parseInt(filters.page, 10) || 1);
    const pageSize = Math.min(50, Math.max(1, parseInt(filters.pageSize, 10) || 20));
    const conditions = [];
    const params = [];

    if (keyword) {
      conditions.push('(phone LIKE ? OR nickname LIKE ?)');
      params.push(`%${keyword}%`, `%${keyword}%`);
    }
    if (status && status !== 'all') {
      conditions.push('status = ?');
      params.push(status);
    }
    if (role && role !== 'all') {
      conditions.push('role = ?');
      params.push(role);
    }

    const where = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';

    const [countResult] = await query(
      `SELECT COUNT(*) AS total FROM users ${where}`,
      params
    );

    const offset = (page - 1) * pageSize;
    const rows = await query(
      `SELECT ${USER_FIELDS} FROM users ${where} ORDER BY created_at DESC LIMIT ? OFFSET ?`,
      [...params, pageSize, offset]
    );

    return { list: rows, total: countResult.total };
  },
};

module.exports = userRepo;
