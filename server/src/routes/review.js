/**
 * 评价路由
 *
 * POST /api/reviews  — 创建评价
 * GET  /api/reviews  — 评价列表（?order_id= 或 ?user_id=）
 */

const { Router } = require('express');
const reviewController = require('../controllers/review');
const { validateBody } = require('../middleware/validate');
const Joi = require('joi');

const router = Router();

router.post(
  '/',
  validateBody(Joi.object({
    order_id: Joi.number().integer().required(),
    reviewee_id: Joi.number().integer().required(),
    communication_score: Joi.number().integer().min(1).max(5).required(),
    punctuality_score: Joi.number().integer().min(1).max(5).required(),
    accuracy_score: Joi.number().integer().min(1).max(5).required(),
    comment: Joi.string().max(500).optional().allow(''),
  })),
  reviewController.create
);

router.get('/', reviewController.list);

module.exports = router;
