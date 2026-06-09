/**
 * IM 路由
 *
 * GET /api/im/user-sig — 获取当前用户的 IM UserSig（需登录）
 */

const { Router } = require('express');
const imController = require('../controllers/im');

const router = Router();

// 注意：JWT 鉴权已在 app.js 中通过 app.use('/api', auth) 全局应用，
// 此处无需重复添加 auth 中间件。
router.get('/user-sig', imController.getUserSig);
router.post('/ensure-account', imController.ensureAccount);

module.exports = router;
