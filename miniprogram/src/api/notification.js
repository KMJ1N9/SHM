/**
 * 通知接口封装
 *
 * 暴露 4 个接口：
 *   - listNotifications(params)  — 通知列表（分页 + 类型筛选）
 *   - unreadCount()             — 未读通知数
 *   - markRead(id)              — 标记单条已读
 *   - markAllRead()             — 全部标记已读
 */

import { get, put } from './index';

/**
 * 获取我的通知列表
 * @param {Object} [params]
 * @param {string} [params.type]    - 通知类型（all / order_update / review_remind / report_result / credit_change）
 * @param {number} [params.page]    - 页码（默认 1）
 * @param {number} [params.pageSize] - 每页条数（默认 20）
 * @returns {Promise<{list: Array, total: number}>}
 */
export function listNotifications(params) {
  return get('/notifications', params);
}

/**
 * 获取未读通知数
 * @returns {Promise<{count: number}>}
 */
export function unreadCount() {
  return get('/notifications/unread-count');
}

/**
 * 标记单条通知为已读
 * @param {number} id - 通知 ID
 * @returns {Promise<null>}
 */
export function markRead(id) {
  return put(`/notifications/${id}/read`);
}

/**
 * 全部标记为已读
 * @returns {Promise<{updated: number}>}
 */
export function markAllRead() {
  return put('/notifications/read-all');
}
