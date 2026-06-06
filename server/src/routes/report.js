/**
 * 举报路由
 *
 * POST /api/reports  — 创建举报
 * GET  /api/reports  — 我的举报列表
 */

const { Router } = require('express');
const reportController = require('../controllers/report');
const { validateBody } = require('../middleware/validate');
const { sensitiveLimiter } = require('../middleware/rate-limiter');
const Joi = require('joi');

const router = Router();

router.post(
  '/',
  sensitiveLimiter,
  validateBody(Joi.object({
    reported_user_id: Joi.number().integer().required(),
    product_id: Joi.number().integer().optional(),
    order_id: Joi.number().integer().optional(),
    type: Joi.string().valid('描述不符', '辱骂骚扰', '疑似骗子', '其他').required(),
    description: Joi.string().required().min(10).max(1000),
    evidence_images: Joi.array().items(Joi.string().uri()).max(6).optional(),
  })),
  reportController.create
);

router.get('/', reportController.list);

module.exports = router;
