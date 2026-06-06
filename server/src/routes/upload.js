/**
 * 上传路由
 *
 * GET /api/upload/cos-credential — 获取 COS STS 临时密钥
 */

const { Router } = require('express');
const uploadController = require('../controllers/upload');

const router = Router();

router.get('/cos-credential', uploadController.getCredential);

module.exports = router;
