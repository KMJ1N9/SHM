/**
 * 订单路由
 *
 * POST /api/orders            — 创建订单（下单）
 * GET  /api/orders            — 我的订单列表
 * GET  /api/orders/:id        — 订单详情
 * PUT  /api/orders/:id/met    — 标记面交
 * PUT  /api/orders/:id/confirm— 确认收货
 * PUT  /api/orders/:id/cancel — 取消订单
 */

const { Router } = require('express');
const orderController = require('../controllers/order');
const { validateBody } = require('../middleware/validate');
const { sensitiveLimiter } = require('../middleware/rate-limiter');
const Joi = require('joi');

const router = Router();

// 下单（限流 + 校验）
router.post(
  '/',
  sensitiveLimiter,
  validateBody(Joi.object({
    product_id: Joi.number().integer().required(),
  })),
  orderController.create
);

// 列表
router.get('/', orderController.list);

// 详情
router.get('/:id', orderController.detail);

// 标记面交
router.put('/:id/met', orderController.markAsMet);

// 确认收货
router.put('/:id/confirm', orderController.confirm);

// 取消订单
router.put('/:id/cancel', orderController.cancel);

module.exports = router;
