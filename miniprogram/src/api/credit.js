/**
 * 信誉分接口封装
 *
 * 基于 api/index.js 的 get 封装，暴露 2 个接口：
 *   - getMyCredit()        — 我的信誉分 + 变动记录
 *   - getUserCredit(userId) — 查看某用户的公开信誉分
 *
 * 后端路由：
 *   GET /api/credit             → 我的信誉分
 *   GET /api/users/:id/credit   → 某用户公开信誉分
 */

import { get } from './index';

/**
 * 获取我的信誉分详情（含变动记录）
 * @returns {Promise<{score: number, change_log: Array}>}
 */
export function getMyCredit() {
  return get('/credit');
}

/**
 * 查看某用户的公开信誉分
 * @param {number} userId - 用户 ID
 * @returns {Promise<{user_id: number, score: number}>}
 */
export function getUserCredit(userId) {
  return get(`/users/${userId}/credit`);
}
