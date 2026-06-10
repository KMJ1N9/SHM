/**
 * 举报接口封装
 *
 * 基于 api/index.js 的 get/post 封装，暴露 3 个接口：
 *   - createReport(data)       — 创建举报
 *   - getReportList(params)    — 我的举报列表（分页）
 *   - getReportDetail(id)      — 举报详情（含处理进度）
 */

import { get, post } from './index';

/**
 * 创建举报
 * @param {Object} data
 * @param {number} data.reported_user_id - 被举报人 ID（必填）
 * @param {number} [data.product_id]     - 关联商品 ID（选填）
 * @param {number} [data.order_id]       - 关联订单 ID（选填）
 * @param {string} data.type             - 举报类型：描述不符/辱骂骚扰/疑似骗子/其他
 * @param {string} data.description      - 问题描述（10-1000 字）
 * @param {string[]} [data.evidence_images] - 证据截图 URL 数组（选填，最多 6 张）
 * @returns {Promise<Object>}
 */
export function createReport(data) {
  return post('/reports', data);
}

/**
 * 我的举报列表
 * @param {Object} [params]
 * @param {number} [params.page]     - 页码，默认 1
 * @param {number} [params.pageSize] - 每页条数，默认 20
 * @returns {Promise<{list: Array, total: number}>}
 */
export function getReportList(params) {
  return get('/reports', params);
}

/**
 * 举报详情（含举报人/被举报人信息 + 处理进度）
 * @param {number} id - 举报 ID
 * @returns {Promise<Object>}
 */
export function getReportDetail(id) {
  return get(`/reports/${id}`);
}
