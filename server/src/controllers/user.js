/**
 * 用户控制器
 */

const userService = require('../services/user');

const userController = {
  /**
   * GET /api/users/:id — 查看用户公开信息
   */
  async getById(req, res, next) {
    try {
      const user = await userService.getById(parseInt(req.params.id, 10));
      res.json({ code: 0, message: 'ok', data: user });
    } catch (err) {
      next(err);
    }
  },

  /**
   * GET /api/users/cs/contact — 获取客服联系方式
   */
  async getCSContact(req, res, next) {
    try {
      const cs = await userService.getCSContact();
      res.json({ code: 0, message: 'ok', data: cs });
    } catch (err) {
      next(err);
    }
  },

  /**
   * GET /api/users/admin/contact — 获取管理员联系方式
   */
  async getAdminContact(req, res, next) {
    try {
      const admin = await userService.getAdminContact();
      res.json({ code: 0, message: 'ok', data: admin });
    } catch (err) {
      next(err);
    }
  },

  /**
   * PUT /api/users/me — 编辑个人资料
   */
  async updateProfile(req, res, next) {
    try {
      const result = await userService.updateProfile(req.user.id, req.body, req.user.role);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },
};

module.exports = userController;
