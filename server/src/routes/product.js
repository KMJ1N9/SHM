/**
 * 商品路由
 *
 * GET    /api/products       — 商品列表（首页 + 搜索 + 筛选）
 * GET    /api/products/my    — 我发布的商品（⚠️ 必须在 /:id 之前注册）
 * GET    /api/products/:id   — 商品详情
 * POST   /api/products       — 发布商品
 * PUT    /api/products/:id   — 编辑商品
 * DELETE /api/products/:id   — 删除商品（软删除）
 */

const { Router } = require('express');
const productController = require('../controllers/product');
const { validateBody } = require('../middleware/validate');
const { sensitiveLimiter } = require('../middleware/rate-limiter');
const Joi = require('joi');

const router = Router();

// 列表
router.get('/', productController.list);

// 我发布的（必须在 /:id 之前）
router.get('/my', productController.my);

// 详情
router.get('/:id', productController.detail);

// 发布（限流 + 校验）
router.post(
  '/',
  sensitiveLimiter,
  validateBody(Joi.object({
    title: Joi.string().required().min(1).max(200),
    description: Joi.string().max(2000).optional().allow(''),
    category: Joi.string().required(),
    condition: Joi.string().required().valid('全新', '95新', '9成新', '8成新', '7成新及以下'),
    original_price: Joi.number().required().min(0).precision(2),
    price: Joi.number().required().min(0).precision(2),
    trade_location: Joi.string().required().max(200),
    negotiable: Joi.boolean().optional().default(true),
    images: Joi.array().items(Joi.string().uri()).max(6).optional(),
  })),
  productController.create
);

// 编辑
router.put(
  '/:id',
  validateBody(Joi.object({
    title: Joi.string().max(200).optional(),
    description: Joi.string().max(2000).optional().allow(''),
    category: Joi.string().optional(),
    condition: Joi.string().valid('全新', '95新', '9成新', '8成新', '7成新及以下').optional(),
    original_price: Joi.number().min(0).precision(2).optional(),
    price: Joi.number().min(0).precision(2).optional(),
    trade_location: Joi.string().max(200).optional(),
    negotiable: Joi.boolean().optional(),
    images: Joi.array().items(Joi.string().uri()).max(6).optional(),
  })),
  productController.update
);

// 删除
router.delete('/:id', productController.delete);

module.exports = router;
