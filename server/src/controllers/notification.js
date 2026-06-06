/**
 * 通知控制器
 */

const notificationService = require('../services/notification');

const notificationController = {
  /**
   * GET /api/notifications — 我的通知列表
   */
  async list(req, res, next) {
    try {
      const result = await notificationService.list(req.user.id, req.query);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  /**
   * GET /api/notifications/unread-count — 未读通知数
   */
  async unreadCount(req, res, next) {
    try {
      const result = await notificationService.unreadCount(req.user.id);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  /**
   * PUT /api/notifications/:id/read — 标记已读
   */
  async read(req, res, next) {
    try {
      await notificationService.read(parseInt(req.params.id, 10), req.user.id);
      res.json({ code: 0, message: 'ok', data: null });
    } catch (err) {
      next(err);
    }
  },

  /**
   * PUT /api/notifications/read-all — 全部标记已读
   */
  async readAll(req, res, next) {
    try {
      const result = await notificationService.readAll(req.user.id);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },
};

module.exports = notificationController;
