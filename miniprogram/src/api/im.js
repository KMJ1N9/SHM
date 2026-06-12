/**
 * IM 接口封装
 *
 * 暴露 3 个接口：
 *   - getUserSig()      — 获取当前用户的 IM UserSig（用于 IM SDK 登录）
 *   - ensureAccount()   — 确保指定用户的 IM 账号已导入
 *   - getCSContact()    — 获取客服联系方式
 */

import { get, post } from './index';

/**
 * 获取当前用户的 IM 登录凭证
 * @returns {Promise<{userId: string, userSig: string, sdkAppId: number}>}
 */
export function getUserSig() {
  return get('/im/user-sig');
}

/**
 * 获取客服联系方式（ID + 昵称 + 头像）
 * @returns {Promise<{id: number, nickname: string, avatar: string}>}
 */
export function getCSContact() {
  return get('/users/cs/contact');
}

/**
 * 确保指定用户的 IM 账号已导入
 *
 * 腾讯云 IM 发送消息前要求接收方 UserID 已存在于 IM 系统中。
 * SDK login() 会自动创建登录者账号，但从未登录过的接收方
 * 需要通过 REST API 预先导入，否则 sendMessage 返回 20003 错误。
 *
 * 同时传递昵称和头像，避免 IM 服务端因缺少资料而生成
 * "用户X" 占位昵称。
 *
 * @param {string|number} userId - 要确保存在的 UserID
 * @param {string} [nick] - 用户昵称（可选）
 * @param {string} [avatar] - 头像 URL（可选）
 * @returns {Promise<{imported: boolean}>}
 */
export function ensureAccount(userId, nick, avatar) {
  return post('/im/ensure-account', { userId, nick, avatar });
}
