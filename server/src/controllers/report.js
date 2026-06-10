/**
 * 举报控制器
 */

const reportService = require('../services/report');

const reportController = {
  /**
   * POST /api/reports — 创建举报
   */
  async create(req, res, next) {
    try {
      const report = await reportService.create(req.user.id, req.body);
      res.status(201).json({ code: 0, message: 'ok', data: report });
    } catch (err) {
      next(err);
    }
  },

  /**
   * GET /api/reports — 我的举报列表
   */
  async list(req, res, next) {
    try {
      const result = await reportService.list(req.user.id, req.query);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  /**
   * GET /api/reports/:id — 举报详情
   */
  async detail(req, res, next) {
    try {
      const report = await reportService.detail(
        parseInt(req.params.id, 10),
        req.user.id,
        req.user.role
      );
      res.json({ code: 0, message: 'ok', data: report });
    } catch (err) {
      next(err);
    }
  },
};

module.exports = reportController;
