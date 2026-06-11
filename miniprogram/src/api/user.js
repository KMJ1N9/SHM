/**
 * 用户接口封装
 *
 * 暴露：
 *   - getPublicProfile(userId) — 获取用户公开信息（昵称、头像等）
 */

import { get, put } from './index';

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

/**
 * 更新当前用户个人资料
 *
 * @param {Object} data
 * @param {string} [data.nickname]      - 新昵称
 * @param {string} [data.avatar]        - 新头像 URL
 * @param {string} [data.class_name]    - 班级
 * @param {string} [data.dorm_building] - 宿舍楼栋
 * @returns {Promise<Object>} 更新后的用户信息
 */
export function updateProfile(data) {
  return put('/users/me', data);
}
