/**
 * 腾讯云 IM REST API 底层封装
 *
 * 本模块封装 IM REST API 的 URL 签名和 HTTP 调用逻辑，
 * 供 services/im/tencent.js Provider 使用。
 *
 * 职责：
 * - 生成 REST API 请求 URL（含 UserSig 签名）
 * - 统一错误处理和日志
 *
 * UserSig 生成使用官方 tls-sig-api-v2 库（TLS 2.0 标准），
 * 不使用自定义签名实现。
 */

const axios = require('axios');
const { Api: TLSSigAPIv2 } = require('tls-sig-api-v2');
const config = require('../config');
const logger = require('./logger').business;

const IM_REST_BASE = 'https://console.tim.qq.com';

/** TLS 签名器单例（sdkAppId + secretKey 确定唯一实例） */
const sigApi = new TLSSigAPIv2(config.im.sdkAppId, config.im.secretKey);

/**
 * 生成 UserSig（使用官方 tls-sig-api-v2 库）
 *
 * 文档：https://cloud.tencent.com/document/product/269/32688
 *
 * @param {string|number} userId - IM UserID
 * @param {number} [expire=604800] - 有效期（秒），默认 7 天
 * @returns {string}
 */
function generateUserSig(userId, expire = 604800) {
  return sigApi.genUserSig(String(userId), expire);
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

/**
 * 导入 IM 账号
 *
 * 腾讯云 IM 发送消息前要求接收方账号已存在。
 * SDK login() 会自动创建登录者的账号，但从未登录过的用户
 * （如从未打开小程序的卖家）的账号需要通过 REST API 导入。
 *
 * 同时设置 Nick 和 FaceUrl —— 避免 IM 服务端因缺少资料
 * 而自动生成 "用户X" 这类占位昵称，导致前端会话列表显示
 * 错误名称。
 *
 * API：https://cloud.tencent.com/document/product/269/1608
 *
 * @param {string|number} userId - 要导入的 UserID
 * @param {string} [nick] - 用户昵称（可选，传入则覆盖 IM 中的现有昵称）
 * @param {string} [faceUrl] - 头像 URL（可选）
 * @returns {Promise<{success: boolean, data?: Object, error?: Object}>}
 */
async function importAccount(userId, nick, faceUrl) {
  const body = { UserID: String(userId) };
  if (nick) body.Nick = nick;
  if (faceUrl) body.FaceUrl = faceUrl;
  return callRestApi('/v4/im_open_login_svc/account_import', body);
}

module.exports = { generateUserSig, buildRestUrl, callRestApi, importAccount };
