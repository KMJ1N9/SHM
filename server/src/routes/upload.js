/**
 * 上传路由
 *
 * GET  /api/upload/cos-credential — 获取 COS STS 临时密钥
 * POST /api/upload/image          — 开发环境服务端图片上传（COS 占位符时回退）
 */

const { Router } = require('express');
const uploadController = require('../controllers/upload');

const router = Router();

router.get('/cos-credential', uploadController.getCredential);
router.post('/image', uploadController.uploadImage);

module.exports = router;
