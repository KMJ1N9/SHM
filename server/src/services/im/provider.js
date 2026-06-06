/**
 * IM Provider 抽象接口
 *
 * 定义即时通讯服务必须实现的方法。
 * 业务层通过本接口调用 IM 功能，不直接依赖具体 IM 厂商 SDK。
 * 后续更换 IM 服务商只需新增 Provider 实现（如 tencent.js → netease.js）。
 *
 * 使用方式：
 *   const imProvider = require('./im');
 *   await imProvider.sendSystemMessage(userId, message);
 */

// 获取当前 IM Provider 实现（默认腾讯云 IM）
// 后续可通过环境变量 IM_PROVIDER 切换
const providerName = process.env.IM_PROVIDER || 'tencent';
const provider = require(`./${providerName}`);

/**
 * @typedef {Object} SystemMessage
 * @property {string} title - 消息标题
 * @property {string} content - 消息内容
 * @property {Object} [extra] - 扩展字段（跳转路由等）
 */

/**
 * @typedef {Object} IMProvider
 * @property {function(number): string} generateUserSig - 生成 UserSig（HMAC-SHA256）
 * @property {function(number, number): Promise<string>} createConversation - 创建单聊会话
 * @property {function(number, SystemMessage): Promise<void>} sendSystemMessage - 推送系统消息
 */

// 验证 Provider 实现了所有必需方法
const REQUIRED_METHODS = ['generateUserSig', 'createConversation', 'sendSystemMessage'];
for (const method of REQUIRED_METHODS) {
  if (typeof provider[method] !== 'function') {
    throw new Error(
      `[IM Provider] ${providerName}.js 未实现必需的 ${method}() 方法。`
    );
  }
}

module.exports = provider;
