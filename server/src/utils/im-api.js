/**
 * 腾讯云 IM REST API 底层封装
 *
 * 本模块封装 IM REST API 的 URL 签名和 HTTP 调用逻辑，
 * 供 services/im/tencent.js Provider 使用。
 *
 * 职责：
 * - 生成 REST API 请求 URL（含 UserSig 签名）
 * - 统一错误处理和日志
 */

const crypto = require('crypto');
const axios = require('axios');
const config = require('../config');
const logger = require('./logger').business;

const IM_REST_BASE = 'https://console.tim.qq.com';

/**
 * 生成 UserSig（HMAC-SHA256 本地签名）
 *
 * 文档：https://cloud.tencent.com/document/product/269/32688
 *
 * @param {string} userId - IM UserID
 * @param {number} [expire=604800] - 有效期（秒），默认 7 天
 * @returns {string}
 */
function generateUserSig(userId, expire = 604800) {
  const { sdkAppId, secretKey } = config.im;
  const current = Math.floor(Date.now() / 1000);

  const sigDoc = {
    'TLS.ver': '2.0',
    'TLS.identifier': String(userId),
    'TLS.sdkappid': sdkAppId,
    'TLS.expire': expire,
    'TLS.time': current,
  };

  // Base64Url 编码
  const base64Url = (str) =>
    Buffer.from(str)
      .toString('base64')
      .replace(/=/g, '')
      .replace(/\+/g, '*')
      .replace(/\//g, '-');

  const sigContent = base64Url(JSON.stringify(sigDoc));
  const hmac = crypto.createHmac('sha256', secretKey);
  hmac.update(sigContent);
  const sig = base64Url(hmac.digest());

  return `${sigContent}.${sig}.2.${userId.length}.${userId}.${sdkAppId}.${expire}.${current}`;
}

/**
 * 构造 REST API 请求 URL
 * @param {string} path - API 路径（如 /v4/openim/sendmsg）
 * @param {string} [identifier] - 管理员账号，默认 im.adminAccount
 * @returns {string}
 */
function buildRestUrl(path, identifier) {
  const { sdkAppId, adminAccount } = config.im;
  const ident = identifier || adminAccount;
  const userSig = generateUserSig(ident);
  const random = Math.floor(Math.random() * 4294967295);

  return `${IM_REST_BASE}${path}?sdkappid=${sdkAppId}&identifier=${encodeURIComponent(ident)}&usersig=${encodeURIComponent(userSig)}&random=${random}&contenttype=json`;
}

/**
 * 调用 IM REST API
 *
 * @param {string} path - API 路径
 * @param {Object} body - 请求体
 * @returns {Promise<{success: boolean, data?: Object, error?: Object}>}
 */
async function callRestApi(path, body) {
  const url = buildRestUrl(path);
  try {
    const resp = await axios.post(url, body, { timeout: 8000 });

    if (resp.data.ErrorCode && resp.data.ErrorCode !== 0) {
      logger.error('IM REST API 错误', {
        path,
        errorCode: resp.data.ErrorCode,
        errorInfo: resp.data.ErrorInfo,
      });
      return { success: false, error: resp.data };
    }

    return { success: true, data: resp.data };
  } catch (err) {
    logger.error('IM REST API 调用失败', { path, error: err.message });
    return { success: false, error: { ErrorCode: -1, ErrorInfo: err.message } };
  }
}

module.exports = { generateUserSig, buildRestUrl, callRestApi };
