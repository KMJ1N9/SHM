/**
 * 商品接口封装
 *
 * 基于 api/index.js 的 get/post/put/del 封装，暴露 6 个接口：
 *   - list(params)       — 商品列表（首页瀑布流 + 搜索 + 筛选）
 *   - detail(id)         — 商品详情（含卖家公开信息）
 *   - create(data)       — 发布商品
 *   - update(id, data)   — 编辑商品
 *   - remove(id)         — 删除商品（软删除）
 *   - my(params)         — 我发布的商品列表
 *   - getCredential()     — 获取 COS STS 临时凭证
 */

import { get, post, put, del } from './index';

/**
 * 商品列表（首页瀑布流 + 搜索 + 筛选 + 排序）
 * @param {Object} [params]
 * @param {string} [params.keyword]    - 搜索词
 * @param {string} [params.category]   - 分类筛选
 * @param {string} [params.condition]  - 成色筛选
 * @param {number} [params.priceMin]   - 最低价
 * @param {number} [params.priceMax]   - 最高价
 * @param {string} [params.sort]       - 排序：latest | priceAsc | priceDesc
 * @param {number} [params.page]       - 页码，默认 1
 * @param {number} [params.pageSize]   - 每页条数，默认 20
 * @returns {Promise<{list: Array, total: number, page: number, pageSize: number}>}
 */
export function list(params) {
  return get('/products', params);
}

/**
 * 商品详情（含卖家公开信息）
 * @param {number} id
 * @returns {Promise<Object>}
 */
export function detail(id) {
  return get(`/products/${id}`);
}

/**
 * 发布商品
 * @param {Object} data
 * @param {string}   data.title          - 商品标题（1-200 字符）
 * @param {string}   [data.description]  - 商品描述（0-2000 字符）
 * @param {string}   data.category       - 分类
 * @param {string}   data.condition      - 成色
 * @param {number}   data.original_price - 原价
 * @param {number}   data.price          - 售价
 * @param {string}   data.trade_location - 交易地点
 * @param {boolean}  [data.negotiable]   - 是否可议价
 * @param {string[]} data.images         - 商品图片 URL 列表（1-6 张）
 * @returns {Promise<Object>}
 */
export function create(data) {
  return post('/products', data);
}

/**
 * 编辑商品
 * @param {number} id
 * @param {Object} data - 所有字段可选
 * @returns {Promise<Object>}
 */
export function update(id, data) {
  return put(`/products/${id}`, data);
}

/**
 * 删除商品（软删除）
 * @param {number} id
 * @returns {Promise<void>}
 */
export function remove(id) {
  return del(`/products/${id}`);
}

/**
 * 我发布的商品列表
 * @param {Object} [params]
 * @param {string} [params.status]   - 筛选状态：active | reserved | sold | deleted | off_shelf | all
 * @param {number} [params.page]     - 页码
 * @param {number} [params.pageSize] - 每页条数
 * @returns {Promise<{list: Array, total: number}>}
 */
export function my(params) {
  return get('/products/my', params);
}

/**
 * 获取 COS 临时上传凭证
 * @returns {Promise<{credentials: Object, policy: string, expiredTime: number, prefix: string, bucket: string, region: string, cdnBaseUrl: string}>}
 */
export function getCredential() {
  return get('/upload/cos-credential');
}
