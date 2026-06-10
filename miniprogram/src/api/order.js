/**
 * 订单接口封装
 *
 * 基于 api/index.js 的 get/post/put 封装，暴露 6 个接口：
 *   - createOrder(data)     — 创建订单（下单）
 *   - getOrderList(params)  — 我的订单列表
 *   - getOrderDetail(id)    — 订单详情
 *   - markAsMet(id)         — 标记面交
 *   - confirmOrder(id)      — 确认收货
 *   - cancelOrder(id)       — 取消订单
 */

import { get, post, put } from './index';

/**
 * 创建订单（下单）
 * @param {Object} data
 * @param {number} data.product_id - 商品 ID
 * @returns {Promise<{order: Object, created: boolean}>}
 */
export function createOrder(data) {
  return post('/orders', data);
}

/**
 * 我的订单列表（默认返回我作为买家/卖家的全部订单）
 * @param {Object} [params]
 * @param {string} [params.role]    - 角色筛选：buyer | seller
 * @param {string} [params.status]  - 状态筛选：pending | met | completed | cancelled
 * @param {number} [params.page]    - 页码，默认 1
 * @param {number} [params.pageSize] - 每页条数，默认 20
 * @returns {Promise<{list: Array, total: number, page: number, pageSize: number}>}
 */
export function getOrderList(params) {
  return get('/orders', params);
}

/**
 * 订单详情（仅买方或卖方可查看）
 * @param {number} id - 订单 ID
 * @returns {Promise<Object>}
 */
export function getOrderDetail(id) {
  return get(`/orders/${id}`);
}

/**
 * 标记面交（买方或卖方任一方点击"已面交"）
 * 仅 pending 状态可操作，操作后订单状态变为 met
 * @param {number} id - 订单 ID
 * @returns {Promise<Object>}
 */
export function markAsMet(id) {
  return put(`/orders/${id}/met`);
}

/**
 * 确认收货（仅买家可操作）
 * 仅 met 状态可操作，操作后订单状态变为 completed，商品变为 sold
 * @param {number} id - 订单 ID
 * @returns {Promise<Object>}
 */
export function confirmOrder(id) {
  return put(`/orders/${id}/confirm`);
}

/**
 * 取消订单
 * pending 状态：买家/卖家均可取消
 * met 状态：仅买家可取消
 * @param {number} id - 订单 ID
 * @returns {Promise<Object>}
 */
export function cancelOrder(id) {
  return put(`/orders/${id}/cancel`);
}
