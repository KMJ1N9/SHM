/**
 * 认证控制器
 *
 * 只做参数提取和响应组装，所有业务逻辑在 services/auth.js 中。
 */

const authService = require('../services/auth');

const authController = {
  /**
   * POST /api/auth/login — 微信手机号登录
   */
  async login(req, res, next) {
    try {
      const { code } = req.body;
      const result = await authService.login(code);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  /**
   * POST /api/auth/refresh — 刷新 Token
   */
  async refresh(req, res, next) {
    try {
      const { refresh_token } = req.body;
      const result = await authService.refresh(refresh_token);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  /**
   * GET /api/auth/me — 获取当前用户信息
   */
  async me(req, res, next) {
    try {
      const user = await authService.me(req.user.id);
      res.json({ code: 0, message: 'ok', data: user });
    } catch (err) {
      next(err);
    }
  },
};

module.exports = authController;
