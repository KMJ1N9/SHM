/**
 * 管理后台路由
 *
 * 工单管理（需客服权限）：
 *   GET  /api/admin/tickets              — 工单列表
 *   PUT  /api/admin/tickets/:id/process  — 受理工单
 *   PUT  /api/admin/tickets/:id/resolve  — 裁决工单
 *
 * 用户管理（需管理员权限）：
 *   GET  /api/admin/users                — 用户列表
 *   PUT  /api/admin/users/:id/ban        — 封禁用户
 *   PUT  /api/admin/users/:id/unban      — 解封用户
 *
 * 商品管理（需管理员权限）：
 *   PUT  /api/admin/products/:id/off-shelf — 下架商品
 *
 * 数据统计（需管理员权限）：
 *   GET  /api/admin/analytics/overview       — 平台数据概览
 *   GET  /api/admin/analytics/categories     — 热门分类
 *   GET  /api/admin/analytics/search-keywords— 热门搜索词
 *   GET  /api/admin/dashboard                — 数据看板
 *
 * 敏感词库（需管理员权限）：
 *   GET  /api/admin/sensitive/stats    — 词库统计
 *   POST /api/admin/sensitive/reload   — 重新加载词库
 *   POST /api/admin/sensitive/check    — 文本检查
 *
 * 审计日志（需管理员权限）：
 *   GET  /api/admin/logs — 操作日志列表
 */

const { Router } = require('express');
const adminController = require('../controllers/admin');
const cs = require('../middleware/cs');
const admin = require('../middleware/admin');
const { validateBody } = require('../middleware/validate');
const Joi = require('joi');

const router = Router();

// ---- 工单管理（客服 + 管理员） ----
const csRouter = Router();
csRouter.get('/tickets', adminController.listTickets);
csRouter.put('/tickets/:id/process', adminController.processTicket);
csRouter.put(
  '/tickets/:id/resolve',
  validateBody(Joi.object({
    resolution: Joi.string().required().min(1),
    deduct_credit: Joi.number().integer().min(0).max(100).optional(),
  })),
  adminController.resolveTicket
);

// ---- 用户管理（仅管理员） ----
const adminRouter = Router();
adminRouter.get('/users', adminController.listUsers);
adminRouter.put('/users/:id/ban', adminController.banUser);
adminRouter.put('/users/:id/unban', adminController.unbanUser);

// ---- 商品管理（仅管理员） ----
adminRouter.put('/products/:id/off-shelf', adminController.offShelfProduct);

// ---- 数据统计（仅管理员） ----
adminRouter.get('/analytics/overview', adminController.overview);
adminRouter.get('/analytics/categories', adminController.categories);
adminRouter.get('/analytics/search-keywords', adminController.searchKeywords);
adminRouter.get('/dashboard', adminController.dashboard);

// ---- 敏感词库（仅管理员） ----
adminRouter.get('/sensitive/stats', adminController.sensitiveStats);
adminRouter.post('/sensitive/reload', adminController.reloadSensitive);
adminRouter.post(
  '/sensitive/check',
  validateBody(Joi.object({
    text: Joi.string().required().min(1).max(5000),
  })),
  adminController.checkSensitive
);

// ---- 审计日志（仅管理员） ----
adminRouter.get('/logs', adminController.listLogs);

// 挂载中间件
router.use('/', cs, csRouter);
router.use('/', admin, adminRouter);

module.exports = router;
