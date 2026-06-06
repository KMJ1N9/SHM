/**
 * 管理员权限中间件
 *
 * 检查 req.user.role === 'admin'。
 * 必须在 auth 中间件之后使用（依赖 req.user）。
 *
 * 使用方式：
 *   router.put('/admin/users/:id/ban', auth, admin, controller.ban);
 */

const errors = require('../utils/errors');

function admin(req, res, next) {
  if (!req.user || req.user.role !== 'admin') {
    throw errors.needAdmin();
  }
  next();
}

module.exports = admin;
