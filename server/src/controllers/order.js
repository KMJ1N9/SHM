/**
 * 订单控制器
 */

const orderService = require('../services/order');

const orderController = {
  /**
   * POST /api/orders — 创建订单（下单）
   */
  async create(req, res, next) {
    try {
      const order = await orderService.create(
        req.user.id,
        req.user.credit_score,
        req.body
      );
      res.status(201).json({ code: 0, message: 'ok', data: order });
    } catch (err) {
      next(err);
    }
  },

  /**
   * GET /api/orders — 我的订单列表
   */
  async list(req, res, next) {
    try {
      const result = await orderService.list(req.user.id, req.query);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  /**
   * GET /api/orders/:id — 订单详情
   */
  async detail(req, res, next) {
    try {
      const order = await orderService.detail(
        parseInt(req.params.id, 10),
        req.user.id
      );
      res.json({ code: 0, message: 'ok', data: order });
    } catch (err) {
      next(err);
    }
  },

  /**
   * PUT /api/orders/:id/met — 标记面交
   */
  async markAsMet(req, res, next) {
    try {
      const order = await orderService.markAsMet(
        parseInt(req.params.id, 10),
        req.user.id
      );
      res.json({ code: 0, message: 'ok', data: order });
    } catch (err) {
      next(err);
    }
  },

  /**
   * PUT /api/orders/:id/confirm — 确认收货
   */
  async confirm(req, res, next) {
    try {
      const order = await orderService.confirm(
        parseInt(req.params.id, 10),
        req.user.id
      );
      res.json({ code: 0, message: 'ok', data: order });
    } catch (err) {
      next(err);
    }
  },

  /**
   * PUT /api/orders/:id/cancel — 取消订单
   */
  async cancel(req, res, next) {
    try {
      const order = await orderService.cancel(
        parseInt(req.params.id, 10),
        req.user.id
      );
      res.json({ code: 0, message: 'ok', data: order });
    } catch (err) {
      next(err);
    }
  },
};

module.exports = orderController;
