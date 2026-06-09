/**
 * 认证路由
 *
 * POST /api/auth/login    — 微信手机号登录
 * POST /api/auth/refresh  — 刷新 Token
 * GET  /api/auth/me       — 获取当前用户信息
 */

const { Router } = require('express');
const authController = require('../controllers/auth');
const { validateBody } = require('../middleware/validate');
const { sensitiveLimiter } = require('../middleware/rate-limiter');
const Joi = require('joi');

const router = Router();

router.post(
  '/login',
  sensitiveLimiter,
  validateBody(Joi.object({
    code: Joi.string().required(),
  })),
  authController.login
);

router.post(
  '/refresh',
  validateBody(Joi.object({
    refresh_token: Joi.string().required(),
  })),
  authController.refresh
);

router.get('/me', authController.me);

module.exports = router;
