/**
 * 管理后台服务
 *
 * 处理工单管理、用户管理、商品管理、敏感词库管理、审计日志查询。
 */

const config = require('../config');
const reportRepo = require('../repository/report');
const userRepo = require('../repository/user');
const productRepo = require('../repository/product');
const {
  notFound, ticketStateInvalid, cannotOperateAdmin,
} = require('../utils/errors');
const logger = require('../utils/logger').business;

const adminService = {
  // ============================================================
  // 工单管理
  // ============================================================

  /**
   * 受理工单（标记为 processing）
   * @param {number} ticketId
   * @param {number} adminId
   * @returns {Promise<Object>}
   */
  async processTicket(ticketId, adminId) {
    const ticket = await reportRepo.findById(ticketId);
    if (!ticket) {
      throw notFound('工单');
    }
    if (ticket.status !== 'pending') {
      throw ticketStateInvalid('工单已被其他人受理');
    }

    const updated = await reportRepo.updateStatus(ticketId, 'processing');

    // 记录操作日志
    await reportRepo.createAdminLog({
      admin_id: adminId,
      action: 'process_ticket',
      target_type: 'ticket',
      target_id: ticketId,
    });

    logger.info('工单受理', { ticketId, adminId });
    return updated;
  },

  /**
   * 裁决工单
   * @param {number} ticketId
   * @param {number} adminId
   * @param {Object} data - { resolution, deduct_credit? }
   * @returns {Promise<Object>}
   */
  async resolveTicket(ticketId, adminId, data) {
    const ticket = await reportRepo.findById(ticketId);
    if (!ticket) {
      throw notFound('工单');
    }
    if (ticket.status !== 'processing') {
      throw ticketStateInvalid('仅已受理的工单可裁决');
    }

    // 事务内原子操作：工单裁决 + 信誉分扣减 + 变动通知
    const result = await reportRepo.resolveWithPenalty({
      ticketId,
      resolution: data.resolution,
      reportedUserId: ticket.reported_user_id,
      deductCredit: data.deduct_credit || 0,
      creditMax: config.credit.max,
    });

    // 记录管理员操作日志（非关键路径，事务外执行）
    await reportRepo.createAdminLog({
      admin_id: adminId,
      action: 'resolve_ticket',
      target_type: 'ticket',
      target_id: ticketId,
      reason: data.resolution,
    });

    logger.info('工单裁决', { ticketId, adminId, deductCredit: data.deduct_credit || 0 });
    return result.ticket;
  },

  /**
   * 工单列表（客服/管理端）
   * @param {Object} filters
   * @returns {Promise<{list: Array, total: number}>}
   */
  async listTickets(filters) {
    return reportRepo.list(filters);
  },

  // ============================================================
  // 用户管理
  // ============================================================

  /**
   * 用户列表
   * @param {Object} filters
   * @returns {Promise<{list: Array, total: number}>}
   */
  async listUsers(filters) {
    return userRepo.listWithFilters(filters);
  },

  /**
   * 封禁用户
   * @param {number} userId - 被操作的用户 ID
   * @param {number} adminId - 管理员 ID
   * @returns {Promise<Object>}
   */
  async banUser(userId, adminId) {
    const user = await userRepo.findById(userId);
    if (!user) {
      throw notFound('用户');
    }
    if (user.role === 'admin') {
      throw cannotOperateAdmin();
    }

    const updated = await userRepo.updateStatus(userId, 'banned');

    await reportRepo.createAdminLog({
      admin_id: adminId,
      action: 'ban',
      target_type: 'user',
      target_id: userId,
    });

    logger.info('用户封禁', { userId, adminId });
    return updated;
  },

  /**
   * 解封用户
   * @param {number} userId
   * @param {number} adminId
   * @returns {Promise<Object>}
   */
  async unbanUser(userId, adminId) {
    const user = await userRepo.findById(userId);
    if (!user) {
      throw notFound('用户');
    }

    const updated = await userRepo.updateStatus(userId, 'active');

    await reportRepo.createAdminLog({
      admin_id: adminId,
      action: 'unban',
      target_type: 'user',
      target_id: userId,
    });

    logger.info('用户解封', { userId, adminId });
    return updated;
  },

  // ============================================================
  // 商品管理
  // ============================================================

  /**
   * 管理端商品列表（含全部状态：active/reserved/sold/off_shelf/deleted）。
   *
   * 与 productService.list() 保持一致的响应格式：
   *   - cover_image：从 images JSON 数组提取第一张作为封面
   *   - seller：嵌套对象 { id, nickname, avatar, credit_score }
   *
   * @param {Object} filters
   * @returns {Promise<{list: Array, total: number, page: number, pageSize: number}>}
   */
  async listAllProducts(filters) {
    const result = await productRepo.listAll(filters);
    // 构造封面图 URL（从 images JSON 数组提取第一张）。
    // mysql2 将 JSON 列解析为对象，但部分旧数据可能仍为字符串。
    const extractCoverImage = (images) => {
      if (!images) return null;
      let arr = images;
      if (typeof arr === 'string') {
        try { arr = JSON.parse(arr); } catch { return null; }
      }
      return Array.isArray(arr) && arr.length > 0 ? arr[0] : null;
    };
    // 将扁平 seller_* 字段嵌套为 seller 对象，与 productService.list() 保持一致
    result.list = result.list.map((row) => ({
      ...row,
      cover_image: extractCoverImage(row.images),
      seller: {
        id: row.seller_id,
        nickname: row.seller_nickname,
        avatar: row.seller_avatar,
        credit_score: row.seller_credit_score,
      },
    }));
    return result;
  },

  /**
   * 下架商品
   * @param {number} productId
   * @param {number} adminId
   * @returns {Promise<Object>}
   */
  async offShelfProduct(productId, adminId) {
    const product = await productRepo.findById(productId);
    if (!product) {
      throw notFound('商品');
    }

    const updated = await productRepo.updateStatus(productId, 'off_shelf');

    await reportRepo.createAdminLog({
      admin_id: adminId,
      action: 'off_shelf',
      target_type: 'product',
      target_id: productId,
    });

    logger.info('商品下架', { productId, adminId });
    return updated;
  },

  // ============================================================
  // 敏感词库管理
  // ============================================================

  /**
   * 敏感词库统计
   * @returns {Promise<Object>}
   */
  async sensitiveStats() {
    const sensitiveFilter = require('../utils/sensitive-filter');
    return {
      word_count: sensitiveFilter.getWordCount(),
    };
  },

  /**
   * 重新加载敏感词库
   * @param {number} adminId
   * @returns {Promise<Object>}
   */
  async reloadSensitive(adminId) {
    const sensitiveFilter = require('../utils/sensitive-filter');
    sensitiveFilter.reload();

    logger.info('敏感词库重新加载', { adminId });
    return { success: true, word_count: sensitiveFilter.getWordCount() };
  },

  /**
   * 检查文本是否包含敏感词
   * @param {string} text
   * @returns {Promise<{has_sensitive: boolean, words?: string[]}>}
   */
  async checkSensitive(text) {
    const sensitiveFilter = require('../utils/sensitive-filter');
    const result = sensitiveFilter.check(text);
    return {
      has_sensitive: result.hasSensitive,
      words: result.words || [],
    };
  },

  // ============================================================
  // 审计日志
  // ============================================================

  /**
   * 操作审计日志列表
   * @param {Object} filters
   * @returns {Promise<{list: Array, total: number}>}
   */
  async listLogs(filters) {
    return reportRepo.listAdminLogs(filters);
  },
};

module.exports = adminService;
