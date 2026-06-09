/**
 * 认证接口封装
 *
 * 基于 api/index.js 的 POST/GET 封装，暴露 3 个接口：
 *   - login(code)     — 微信手机号授权登录（skipAuth，不附加 Token）
 *   - refreshToken(tok) — 刷新 access_token（skipAuth，避免拦截器死循环）
 *   - getMe()         — 获取当前登录用户信息（走标准拦截器）
 */

import { post, get } from './index';

/**
 * 微信手机号授权登录（注册/登录合一）
 * @param {string} code - wx.getPhoneNumber 返回的 code
 * @returns {Promise<{accessToken: string, refreshToken: string, isNewUser: boolean, user: Object}>}
 */
export function login(code) {
  return post('/auth/login', { code }, true);
}

/**
 * 刷新 access_token
 *
 * 注意：此接口不走标准拦截器（skipAuth=true），避免 1002 错误触发自身导致死循环。
 * @param {string} refreshToken - 长期刷新令牌
 * @returns {Promise<{accessToken: string, refreshToken: string}>}
 */
export function refreshToken(refreshToken) {
  return post('/auth/refresh', { refresh_token: refreshToken }, true);
}

/**
 * 获取当前登录用户信息
 * @returns {Promise<Object>} 用户信息对象
 */
export function getMe() {
  return get('/auth/me');
}
