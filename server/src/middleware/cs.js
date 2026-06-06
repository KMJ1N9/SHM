/**
 * 客服权限中间件
 *
 * 检查 req.user.role 为 'cs' 或 'admin'。
 * 必须在 auth 中间件之后使用（依赖 req.user）。
 *
 * 使用方式：
 *   router.get('/admin/tickets', auth, cs, controller.listTickets);
 */

const errors = require('../utils/errors');

function cs(req, res, next) {
  if (!req.user || (req.user.role !== 'cs' && req.user.role !== 'admin')) {
    throw errors.needCS();
  }
  next();
}

module.exports = cs;
