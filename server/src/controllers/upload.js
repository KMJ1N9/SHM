/**
 * 上传控制器
 *
 * 处理 COS STS 临时密钥发放 + 开发环境服务端图片上传（COS 占位符时回退）。
 * 生产环境：前端拿临时密钥直传 COS，图片不经过服务器。
 * 开发环境：前端上传到本服务，存储于 public/images/，返回可访问 URL。
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const multer = require('multer');
const { generateCredential } = require('../utils/cos');

/** 允许的 MIME 类型 */
const ALLOWED_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
/** 单文件最大 5MB */
const MAX_SIZE = 5 * 1024 * 1024;

/** multer 配置：存储到 public/images/user_{userId}/，自动创建子目录 */
const storage = multer.diskStorage({
  destination(req, _file, cb) {
    const userId = req.user?.id || 'anon';
    const dir = path.join(__dirname, '..', '..', 'public', 'images', `user_${userId}`);
    fs.mkdirSync(dir, { recursive: true });
    cb(null, dir);
  },
  filename(_req, file, cb) {
    const timestamp = Date.now();
    const random = crypto.randomBytes(4).toString('hex');
    const ext = path.extname(file.originalname) || '.jpg';
    cb(null, `${timestamp}_${random}${ext}`);
  },
});

/** 文件过滤器：只允许图片类型 */
function fileFilter(_req, file, cb) {
  if (ALLOWED_TYPES.includes(file.mimetype)) {
    cb(null, true);
  } else {
    cb(new Error(`不支持的图片格式: ${file.mimetype}`));
  }
}

const upload = multer({ storage, fileFilter, limits: { fileSize: MAX_SIZE } });

const uploadController = {
  /**
   * GET /api/upload/cos-credential — 获取 COS STS 临时密钥
   */
  async getCredential(req, res, next) {
    try {
      const credential = await generateCredential(req.user.id);
      res.json({ code: 0, message: 'ok', data: credential });
    } catch (err) {
      next(err);
    }
  },

  /**
   * POST /api/upload/image — 开发环境服务端图片上传
   *
   * 仅用于 COS 未配置真实凭证时的回退方案。
   * 前端在 credential.mock === true 时调用此端点，
   * 返回可被静态文件服务访问的真实 URL。
   */
  async uploadImage(req, res, next) {
    // 使用 multer 单文件上传中间件
    upload.single('file')(req, res, (err) => {
      if (err) {
        if (err.code === 'LIMIT_FILE_SIZE') {
          return res.status(413).json({
            code: 5010,
            message: '图片大小不能超过 5MB',
            data: null,
          });
        }
        if (err.message && err.message.includes('不支持的图片格式')) {
          return res.status(400).json({
            code: 5011,
            message: err.message,
            data: null,
          });
        }
        return next(err);
      }

      if (!req.file) {
        return res.status(400).json({
          code: 5012,
          message: '请选择要上传的图片',
          data: null,
        });
      }

      // 构造可访问 URL：/images/ 已由 app.js 映射到 public/images/
      const url = `/images/user_${req.user.id}/${req.file.filename}`;

      res.json({
        code: 0,
        message: 'ok',
        data: { url },
      });
    });
  },
};

module.exports = uploadController;
