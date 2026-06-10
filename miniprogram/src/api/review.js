/**
 * 评价接口封装
 *
 * 基于 api/index.js 的 get/post 封装，暴露 3 个接口：
 *   - createReview(data)         — 创建评价（三维评分）
 *   - getReviewsByOrder(orderId) — 获取某订单的评价列表
 *   - getUserReviews(userId, params) — 获取某用户的评价列表（含聚合统计）
 */

import { get, post } from './index';

/**
 * 创建评价（三维评分：沟通态度/守时程度/描述一致度）
 * @param {Object} data
 * @param {number} data.order_id           - 订单 ID
 * @param {number} data.reviewee_id        - 被评价人 ID
 * @param {number} data.communication_score - 沟通态度 1-5
 * @param {number} data.punctuality_score   - 守时程度 1-5
 * @param {number} data.accuracy_score      - 描述一致度 1-5
 * @param {string} [data.comment]           - 文字评价（选填，最长 500 字）
 * @returns {Promise<Object>}
 */
export function createReview(data) {
  return post('/reviews', data);
}

/**
 * 获取某订单的评价列表
 * @param {number} orderId - 订单 ID
 * @returns {Promise<Array>}
 */
export function getReviewsByOrder(orderId) {
  return get('/reviews', { order_id: orderId });
}

/**
 * 获取某用户的评价列表（含三维度平均分统计）
 * @param {number} userId - 用户 ID
 * @param {Object} [params]
 * @param {number} [params.page]     - 页码，默认 1
 * @param {number} [params.pageSize] - 每页条数，默认 20
 * @returns {Promise<{summary: {total: number, avg_communication: number, avg_punctuality: number, avg_accuracy: number}, list: Array, total: number}>}
 */
export function getUserReviews(userId, params) {
  return get('/reviews', { user_id: userId, ...params });
}
