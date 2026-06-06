/**
 * 管理后台控制器
 *
 * 处理工单管理、用户管理、商品管理、敏感词库、审计日志。
 */

const adminService = require('../services/admin');
const analyticsService = require('../services/analytics');

const adminController = {
  // ============================================================
  // 工单管理
  // ============================================================

  /**
   * GET /api/admin/tickets — 工单列表
   */
  async listTickets(req, res, next) {
    try {
      const result = await adminService.listTickets(req.query);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  /**
   * PUT /api/admin/tickets/:id/process — 受理工单
   */
  async processTicket(req, res, next) {
    try {
      const ticket = await adminService.processTicket(
        parseInt(req.params.id, 10),
        req.user.id
      );
      res.json({ code: 0, message: 'ok', data: ticket });
    } catch (err) {
      next(err);
    }
  },

  /**
   * PUT /api/admin/tickets/:id/resolve — 裁决工单
   */
  async resolveTicket(req, res, next) {
    try {
      const ticket = await adminService.resolveTicket(
        parseInt(req.params.id, 10),
        req.user.id,
        req.body
      );
      res.json({ code: 0, message: 'ok', data: ticket });
    } catch (err) {
      next(err);
    }
  },

  // ============================================================
  // 用户管理
  // ============================================================

  /**
   * GET /api/admin/users — 用户列表
   */
  async listUsers(req, res, next) {
    try {
      const result = await adminService.listUsers(req.query);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  /**
   * PUT /api/admin/users/:id/ban — 封禁用户
   */
  async banUser(req, res, next) {
    try {
      const user = await adminService.banUser(
        parseInt(req.params.id, 10),
        req.user.id
      );
      res.json({ code: 0, message: 'ok', data: user });
    } catch (err) {
      next(err);
    }
  },

  /**
   * PUT /api/admin/users/:id/unban — 解封用户
   */
  async unbanUser(req, res, next) {
    try {
      const user = await adminService.unbanUser(
        parseInt(req.params.id, 10),
        req.user.id
      );
      res.json({ code: 0, message: 'ok', data: user });
    } catch (err) {
      next(err);
    }
  },

  // ============================================================
  // 商品管理
  // ============================================================

  /**
   * PUT /api/admin/products/:id/off-shelf — 下架商品
   */
  async offShelfProduct(req, res, next) {
    try {
      const product = await adminService.offShelfProduct(
        parseInt(req.params.id, 10),
        req.user.id
      );
      res.json({ code: 0, message: 'ok', data: product });
    } catch (err) {
      next(err);
    }
  },

  // ============================================================
  // 数据统计
  // ============================================================

  /**
   * GET /api/admin/analytics/overview — 平台数据概览
   */
  async overview(req, res, next) {
    try {
      const result = await analyticsService.overview();
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  /**
   * GET /api/admin/analytics/categories — 热门分类
   */
  async categories(req, res, next) {
    try {
      const result = await analyticsService.categories();
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  /**
   * GET /api/admin/analytics/search-keywords — 热门搜索词
   */
  async searchKeywords(req, res, next) {
    try {
      const result = await analyticsService.searchKeywords();
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  /**
   * GET /api/admin/dashboard — 管理后台数据看板
   */
  async dashboard(req, res, next) {
    try {
      const result = await analyticsService.dashboard();
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  // ============================================================
  // 敏感词库管理
  // ============================================================

  /**
   * GET /api/admin/sensitive/stats — 敏感词库统计
   */
  async sensitiveStats(req, res, next) {
    try {
      const result = await adminService.sensitiveStats();
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  /**
   * POST /api/admin/sensitive/reload — 重新加载敏感词库
   */
  async reloadSensitive(req, res, next) {
    try {
      const result = await adminService.reloadSensitive(req.user.id);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  /**
   * POST /api/admin/sensitive/check — 检查文本
   */
  async checkSensitive(req, res, next) {
    try {
      const result = await adminService.checkSensitive(req.body.text);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  // ============================================================
  // 审计日志
  // ============================================================

  /**
   * GET /api/admin/logs — 操作审计日志列表
   */
  async listLogs(req, res, next) {
    try {
      const result = await adminService.listLogs(req.query);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },
};

module.exports = adminController;
