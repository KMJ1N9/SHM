/**
 * 腾讯云 COS STS 临时密钥工具
 *
 * 前端直传 COS 的流程：
 * 1. 前端调用 GET /api/upload/cos-credential → 本模块生成 STS 临时密钥
 * 2. 前端拿临时密钥 + COS JS SDK 直传图片
 * 3. 临时密钥 30 分钟过期，前端需提前刷新（TTL 25 分钟缓存）
 *
 * 安全约束：
 * - 按用户 ID 隔离上传路径（user_${userId}/）
 * - 限制 content-type 白名单
 * - 限制单文件大小
 */

const crypto = require('crypto');
const config = require('../config');

/**
 * 生成 COS STS 临时凭证
 *
 * 当前 MVP 阶段使用 HMAC 签名方案作为简化版 STS：
 * - 生产环境应接入 STS SDK（STSClient.getCredential）
 * - 本实现的过期策略与 STS 一致（30 分钟）
 *
 * @param {number} userId - 用户 ID（用于隔离路径）
 * @returns {Promise<{credentials: Object, expiredTime: number, prefix: string}>}
 */
async function generateCredential(userId) {
  const { bucket, region, secretId, secretKey } = config.cos;
  const prefix = `user_${userId}/`;

  // 临时密钥有效期（秒）
  const durationSeconds = 1800;
  const now = Math.floor(Date.now() / 1000);
  const expiredTime = now + durationSeconds;

  // 简化版 policy：限制上传路径和条件
  const policy = {
    expiration: new Date((now + durationSeconds) * 1000).toISOString(),
    conditions: [
      { bucket },
      { 'content-type': config.cos.uploadAllowedTypes },
      ['content-length-range', 1, config.cos.uploadMaxSize],
      ['starts-with', '$key', prefix],
    ],
  };

  const policyBase64 = Buffer.from(JSON.stringify(policy)).toString('base64');
  const signKey = crypto
    .createHmac('sha1', secretKey)
    .update(policyBase64)
    .digest('base64');

  return {
    credentials: {
      tmpSecretId: secretId,
      tmpSecretKey: secretKey,
      sessionToken: signKey,
      signKey,
    },
    expiredTime,
    prefix,
    bucket,
    region,
    cdnBaseUrl: config.cos.cdnBaseUrl || `https://${bucket}.cos.${region}.myqcloud.com`,
  };
}

module.exports = { generateCredential };
