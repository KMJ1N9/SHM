/**
 * 上传控制器
 *
 * 处理 COS STS 临时密钥发放。
 * 前端拿临时密钥直传 COS，图片不经过服务器。
 */

const { generateCredential } = require('../utils/cos');

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
};

module.exports = uploadController;
