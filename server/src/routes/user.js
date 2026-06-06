/**
 * 用户路由
 *
 * GET /api/users/:id           — 查看用户公开信息
 * PUT /api/users/me            — 编辑个人资料
 * GET /api/users/:id/credit    — 查看某用户信誉分（公开）
 */

const { Router } = require('express');
const userController = require('../controllers/user');
const creditController = require('../controllers/credit');
const { validateBody } = require('../middleware/validate');
const Joi = require('joi');

const router = Router();

router.get('/:id', userController.getById);

router.put(
  '/me',
  validateBody(Joi.object({
    nickname: Joi.string().max(50).optional(),
    avatar: Joi.string().uri().max(500).optional(),
    class_name: Joi.string().max(100).optional().allow(''),
    dorm_building: Joi.string().max(100).optional().allow(''),
  })),
  userController.updateProfile
);

router.get('/:id/credit', creditController.userPublic);

module.exports = router;
