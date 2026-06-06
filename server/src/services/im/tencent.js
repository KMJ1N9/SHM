/**
 * 腾讯云 IM Provider 实现
 *
 * 封装腾讯云 IM REST API：
 * - generateUserSig: 本地 HMAC-SHA256 签名生成 UserSig（不依赖 IM SDK）
 * - sendSystemMessage: 通过 IM REST API 推送自定义系统消息
 * - createConversation: REST API 创建单聊会话（返回 conversationID）
 *
 * REST API 文档：https://cloud.tencent.com/document/product/269
 */

const crypto = require('crypto');
const axios = require('axios');
const config = require('../../config');
const logger = require('../../utils/logger').business;

const IM_REST_BASE = 'https://console.tim.qq.com';

/**
 * 生成腾讯云 IM UserSig
 *
 * 使用 HMAC-SHA256 算法本地签名，无需依赖腾讯云 IM SDK。
 * 文档：https://cloud.tencent.com/document/product/269/32688
 *
 * @param {number|string} userId - 用户 ID（本系统使用 users.id）
 * @param {number} [expire=604800] - 过期时间（秒），默认 7 天
 * @returns {string} Base64 编码的 UserSig
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

  // HMAC-SHA256 签名
  const hmac = crypto.createHmac('sha256', secretKey);
  hmac.update(sigContent);
  const sig = base64Url(hmac.digest());

  // 组装完整 sig: identifier_length.identifier.sdkappid.expire.time.sign
  const identifier = String(userId);
  return [
    sigContent,
    sig,
    '2',
    identifier.length,
    identifier,
    sdkAppId,
    expire,
    current,
  ].join('.');
}

/**
 * 构造 IM REST API 请求 URL
 * @param {string} path - API 路径
 * @returns {string}
 */
function buildUrl(path) {
  const { sdkAppId, adminAccount, secretKey } = config.im;

  const random = Math.floor(Math.random() * 4294967295);
  const userSig = generateUserSig(adminAccount);

  return `${IM_REST_BASE}${path}?sdkappid=${sdkAppId}&identifier=${encodeURIComponent(adminAccount)}&usersig=${encodeURIComponent(userSig)}&random=${random}&contenttype=json`;
}

/**
 * 调用 IM REST API
 * @param {string} path - API 路径
 * @param {Object} body - 请求体
 * @returns {Promise<Object>} 响应数据
 */
async function restApi(path, body) {
  const url = buildUrl(path);
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
    return { success: false, error: err.message };
  }
}

/**
 * 创建单聊会话
 * @param {number|string} userId1 - 当前用户 ID
 * @param {number|string} userId2 - 对方用户 ID
 * @returns {Promise<string>} conversationID
 */
async function createConversation(userId1, userId2) {
  // 腾讯云 IM 的 conversationID 格式为 C2C${userid}
  // 前端 IM SDK 可直接调用 createConversation，服务端一般不需要创建
  // 返回标准格式的 conversationID 供前端使用
  return `C2C${userId2}`;
}

/**
 * 推送系统消息（通过 IM REST API 发送自定义消息）
 *
 * 用于订单状态变更、评价提醒、举报结果、信誉分变动等系统通知。
 * 消息类型为 TIMCustomElem，前端解析后展示对应 UI。
 *
 * @param {number|string} userId - 接收方用户 ID
 * @param {Object} message
 * @param {string} message.title - 消息标题
 * @param {string} message.content - 消息内容
 * @param {Object} [message.extra] - 扩展字段（如 order_id, report_id, 跳转路径等）
 * @returns {Promise<{success: boolean}>}
 */
async function sendSystemMessage(userId, message) {
  const body = {
    From_Account: config.im.adminAccount,
    MsgRandom: Math.floor(Math.random() * 4294967295),
    MsgBody: [
      {
        MsgType: 'TIMCustomElem',
        MsgContent: {
          Data: JSON.stringify({
            type: 'system',
            title: message.title,
            content: message.content,
            extra: message.extra || {},
          }),
        },
      },
    ],
  };

  // 判断是单聊还是群聊推送
  if (Array.isArray(userId)) {
    // 群发（批量推送）
    body.To_Account = userId.map(String);
  } else {
    body.To_Account = String(userId);
  }

  const result = await restApi('/v4/openim/sendmsg', body);

  if (!result.success) {
    logger.warn('系统消息推送失败', { userId, title: message.title });
  }

  return result;
}

module.exports = {
  generateUserSig,
  createConversation,
  sendSystemMessage,
};
