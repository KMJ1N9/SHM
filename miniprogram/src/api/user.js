/**
 * 用户接口封装
 *
 * 暴露：
 *   - getPublicProfile(userId) — 获取用户公开信息（昵称、头像等）
 */

import { get } from './index';

/**
 * 获取用户公开信息
 *
 * 用于消息列表等场景，当 IM SDK userProfile 返回自动生成的
 * "用户X" 占位名时，从后端 MySQL 获取真实昵称。
 *
 * @param {number} userId
 * @returns {Promise<{id: number, nickname: string, avatar: string|null, class_name: string|null, dorm_building: string|null, credit_score: number}>}
 */
export function getPublicProfile(userId) {
  return get(`/users/${userId}`);
}
