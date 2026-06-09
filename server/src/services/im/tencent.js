/**
 * 腾讯云 IM Provider 实现
 *
 * 封装腾讯云 IM REST API：
 * - generateUserSig: 从 utils/im-api.js 引入（单一实现源）
 * - sendSystemMessage: 通过 IM REST API 推送自定义系统消息
 * - createConversation: REST API 创建单聊会话（返回 conversationID）
 *
 * REST API 文档：https://cloud.tencent.com/document/product/269
 */

const config = require('../../config');
const logger = require('../../utils/logger').business;
const { generateUserSig, buildRestUrl, callRestApi, importAccount } = require('../../utils/im-api');

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

  const result = await callRestApi('/v4/openim/sendmsg', body);

  if (!result.success) {
    logger.warn('系统消息推送失败', { userId, title: message.title });
  }

  return result;
}

module.exports = {
  generateUserSig,
  createConversation,
  sendSystemMessage,
  importAccount,
};
