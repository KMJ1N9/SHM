/**
 * 通知路由
 *
 * GET /api/notifications               — 我的通知列表
 * GET /api/notifications/unread-count  — 未读通知数
 * PUT /api/notifications/:id/read      — 标记单条已读
 * PUT /api/notifications/read-all      — 全部标记已读
 */

const { Router } = require('express');
const notificationController = require('../controllers/notification');

const router = Router();

// ⚠️ 固定路径必须在 /:id 之前注册
router.get('/unread-count', notificationController.unreadCount);
router.put('/read-all', notificationController.readAll);

router.get('/', notificationController.list);
router.put('/:id/read', notificationController.read);

module.exports = router;
