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
   * PUT /api/users/me — 编辑个人资料
   */
  async updateProfile(req, res, next) {
    try {
      const result = await userService.updateProfile(req.user.id, req.body);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },
};

module.exports = userController;
