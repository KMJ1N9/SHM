/**
 * 管理后台接口封装
 *
 * 基于 api/index.js 的 get/post/put 封装，暴露 14 个端点：
 *   工单管理 3 + 用户管理 3 + 商品管理 1 + 数据统计 4 + 敏感词库 3 + 审计日志 1
 *
 * 权限说明：
 *   - 工单管理：cs / admin 角色可访问（路由前缀 /api/admin/tickets）
 *   - 其余端点：仅 admin 角色可访问
 *   - 权限边界由后端 middleware 强制执行，前端仅做 UI 隐藏
 */

import { get, post, put } from './index';

// ============================================================
// 工单管理（cs + admin）
// ============================================================

/**
 * 工单列表
 * @param {Object} [params]
 * @param {string} [params.status]  - 状态过滤：pending/processing/resolved/all
 * @param {string} [params.type]    - 类型过滤
 * @param {number} [params.page]     - 页码
 * @param {number} [params.pageSize] - 每页条数
 * @returns {Promise<{list: Array, total: number}>}
 */
export function getTicketList(params) {
  return get('/admin/tickets', params);
}

/**
 * 受理工单（pending → processing）
 * @param {number} id - 工单 ID
 * @returns {Promise<Object>}
 */
export function processTicket(id) {
  return put(`/admin/tickets/${id}/process`);
}

/**
 * 裁决工单（processing → resolved）
 * @param {number} id - 工单 ID
 * @param {Object} data
 * @param {string} data.resolution      - 处理结论（必填）
 * @param {number} [data.deduct_credit] - 扣减信誉分（0-100，选填）
 * @returns {Promise<Object>}
 */
export function resolveTicket(id, data) {
  return put(`/admin/tickets/${id}/resolve`, data);
}

// ============================================================
// 用户管理（admin only）
// ============================================================

/**
 * 用户列表
 * @param {Object} [params]
 * @param {string} [params.status] - 状态过滤：active/banned
 * @param {number} [params.page]
 * @param {number} [params.pageSize]
 * @returns {Promise<{list: Array, total: number}>}
 */
export function getUserList(params) {
  return get('/admin/users', params);
}

/**
 * 封禁用户
 * @param {number} id - 用户 ID
 * @returns {Promise<Object>}
 */
export function banUser(id) {
  return put(`/admin/users/${id}/ban`);
}

/**
 * 解封用户
 * @param {number} id - 用户 ID
 * @returns {Promise<Object>}
 */
export function unbanUser(id) {
  return put(`/admin/users/${id}/unban`);
}

// ============================================================
// 商品管理（admin only）
// ============================================================

/**
 * 管理端商品列表（含全部状态，不硬编码 active）。
 * @param {Object} [params]
 * @param {string} [params.status]   - 状态筛选：active|reserved|sold|off_shelf|deleted（可选，不传=全部）
 * @param {string} [params.keyword]  - 搜索关键词
 * @param {number} [params.page]
 * @param {number} [params.pageSize]
 * @returns {Promise<{list: Array, total: number, page: number, pageSize: number}>}
 */
export function getAdminProducts(params) {
  return get('/admin/products', params);
}

/**
 * 下架商品
 * @param {number} id - 商品 ID
 * @returns {Promise<Object>}
 */
export function offShelfProduct(id) {
  return put(`/admin/products/${id}/off-shelf`);
}

// ============================================================
// 数据统计（admin only）
// ============================================================

/**
 * 平台数据概览
 * @returns {Promise<{total_users: number, total_products: number, total_orders: number, pending_reports: number, new_users_7d: number, completed_orders_7d: number}>}
 */
export function getAnalyticsOverview() {
  return get('/admin/analytics/overview');
}

/**
 * 热门分类统计
 * @returns {Promise<Array<{category: string, count: number, percentage: number}>>}
 */
export function getAnalyticsCategories() {
  return get('/admin/analytics/categories');
}

/**
 * 热门搜索关键词
 * @returns {Promise<Array<{keyword: string, count: number}>>}
 */
export function getAnalyticsSearchKeywords() {
  return get('/admin/analytics/search-keywords');
}

/**
 * 管理后台数据看板（聚合：概览 + 分类 + 关键词 + 近 7 天趋势）
 * @returns {Promise<Object>}
 */
export function getDashboard() {
  return get('/admin/dashboard');
}

// ============================================================
// 敏感词库（admin only）
// ============================================================

/**
 * 敏感词库统计
 * @returns {Promise<{word_count: number}>}
 */
export function getSensitiveStats() {
  return get('/admin/sensitive/stats');
}

/**
 * 重新加载敏感词库
 * @returns {Promise<{success: boolean, word_count: number}>}
 */
export function reloadSensitiveWords() {
  return post('/admin/sensitive/reload');
}

/**
 * 检查文本是否包含敏感词
 * @param {string} text - 待检查文本
 * @returns {Promise<{has_sensitive: boolean, words?: string[]}>}
 */
export function checkSensitiveText(text) {
  return post('/admin/sensitive/check', { text });
}

// ============================================================
// 审计日志（admin only）
// ============================================================

/**
 * 操作审计日志列表
 * @param {Object} [params]
 * @param {number} [params.admin_id]    - 管理员 ID
 * @param {string} [params.action]      - 操作类型
 * @param {number} [params.page]
 * @param {number} [params.pageSize]
 * @returns {Promise<{list: Array, total: number}>}
 */
export function getAdminLogs(params) {
  return get('/admin/logs', params);
}
