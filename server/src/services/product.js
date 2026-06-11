/**
 * 商品服务
 *
 * 处理商品列表、详情、发布、编辑、删除（软删除）、我发布的。
 */

const config = require('../config');
const db = require('../models/db');
const { cache } = require('../utils/cache');
const productRepo = require('../repository/product');
const sensitiveFilter = require('../utils/sensitive-filter');
const logger = require('../utils/logger').business;
const {
  notFound, invalidStatus, creditTooLowPublish, badRequest,
} = require('../utils/errors');

const MAX_IMAGES = 6;
const MAX_PAGE_SIZE = 50;

/**
 * 从 images JSON 数组提取第一张作为封面
 * @param {string|Array} images - JSON string or parsed array
 * @returns {string|null}
 */
function extractCoverImage(images) {
  if (!images) return null;
  let arr = images;
  if (typeof arr === 'string') {
    try { arr = JSON.parse(arr); } catch { return null; }
  }
  return Array.isArray(arr) && arr.length > 0 ? arr[0] : null;
}

/**
 * 将扁平 seller 字段嵌套为 seller 对象
 * 数据库返回: seller_nickname, seller_avatar, seller_credit_score, ...
 * API 规范: { seller: { id, nickname, avatar, credit_score, ... } }
 */
function nestSeller(row) {
  if (!row || row.seller_nickname === undefined) return row;

  const seller = {
    id: row.seller_id,
    nickname: row.seller_nickname,
    avatar: row.seller_avatar,
    credit_score: row.seller_credit_score,
  };

  // detail 接口额外返回班级、宿舍、评价摘要
  if (row.seller_class_name !== undefined) {
    seller.class_name = row.seller_class_name;
  }
  if (row.seller_dorm_building !== undefined) {
    seller.dorm_building = row.seller_dorm_building;
  }

  // 清理扁平字段
  const result = { ...row };
  delete result.seller_nickname;
  delete result.seller_avatar;
  delete result.seller_credit_score;
  delete result.seller_class_name;
  delete result.seller_dorm_building;

  return { ...result, seller };
}

/**
 * 构建列表缓存 key（按 key 排序确保 JSON 序列化确定性）
 * @param {Object} filters
 * @returns {string}
 */
function buildListCacheKey(filters) {
  const sortedKeys = Object.keys(filters).sort();
  const sorted = {};
  for (const k of sortedKeys) sorted[k] = filters[k];
  return `products:list:${JSON.stringify(sorted)}`;
}

const productService = {
  /**
   * 商品列表（首页瀑布流 + 搜索 + 筛选 + 排序）
   *
   * 自动路由分页模式：
   *   - filters.cursor 存在 → 游标分页（无缓存，O(1) 定位已足够）
   *   - 无 cursor → 偏移分页（LRU 缓存，TTL 60s）
   *
   * @param {Object} filters
   * @returns {Promise<{list: Array, total: number, page?: number, pageSize?: number, cursor?: number, hasMore?: boolean}>}
   */
  async list(filters) {
    // 游标分页（无缓存 — 游标模式每次请求不同，缓存命中率低）
    if (filters.cursor !== undefined) {
      // 非 latest 排序下拒绝游标分页：cursor 基于 id，
      // 而 priceAsc/priceDesc 以价格为主排序键，会导致数据遗漏。
      // 复合游标 (price, id) 待后续实现。
      if (filters.sort && filters.sort !== 'latest') {
        throw badRequest('游标分页仅支持默认排序（latest）');
      }
      const result = await productRepo.listByCursor(filters);
      result.list = result.list.map((row) => ({
        ...nestSeller(row),
        cover_image: extractCoverImage(row.images),
      }));
      return result;
    }

    // 偏移分页（有缓存 — 同一页+筛选组合命中率高）
    const page = Math.max(1, parseInt(filters.page, 10) || 1);
    const pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, parseInt(filters.pageSize, 10) || 20));

    const cacheKey = buildListCacheKey({ ...filters, page, pageSize });

    return cache.getOrSet(cacheKey, async () => {
      const data = await productRepo.list({ ...filters, page, pageSize });
      data.list = data.list.map((row) => ({
        ...nestSeller(row),
        cover_image: extractCoverImage(row.images),
      }));
      return data;
    }, 60 * 1000); // 60s TTL — 首页高并发，允许 1 分钟延迟
  },

  /**
   * 商品详情（含 LRU 缓存）
   *
   * 缓存策略：
   *   - 仅缓存 status='active' 的商品（占绝大多数访问）
   *   - off_shelf 不缓存（低频 + 不同 viewer 权限不同）
   *   - 不存在的 ID 不缓存（防缓存穿透）
   *   - 权限判断在缓存之外执行
   *
   * @param {number} id
   * @param {Object} [viewer] - 当前登录用户（用于权限判断），可选
   * @returns {Promise<Object>}
   */
  async detail(id, viewer) {
    const cacheKey = `product:${id}`;

    // 缓存命中 — 仍需 off_shelf 权限检查（防御性，正常不应缓存 off_shelf）
    let product = cache.get(cacheKey);
    if (product) {
      if (product.status === 'off_shelf') {
        if (!viewer || (viewer.id !== product.seller_id && viewer.role !== 'admin')) {
          throw notFound('商品');
        }
      }
      return nestSeller(product);
    }

    // 缓存 miss — 查询数据库
    product = await productRepo.findById(id);
    if (!product) {
      throw notFound('商品');
    }

    // 仅缓存 active 商品（高频访问的绝大多数场景）
    if (product.status === 'active') {
      cache.set(cacheKey, product, 120 * 1000); // 120s TTL
    }

    // 权限判断（off_shelf 仅卖家和管理员可见）
    if (product.status === 'off_shelf') {
      if (!viewer || (viewer.id !== product.seller_id && viewer.role !== 'admin')) {
        throw notFound('商品');
      }
    }

    return nestSeller(product);
  },

  /**
   * 发布商品
   * @param {number} sellerId
   * @param {number} creditScore - 当前用户信誉分
   * @param {Object} data
   * @returns {Promise<Object>}
   */
  async create(sellerId, creditScore, data) {
    if (creditScore < config.credit.publishThreshold) {
      throw creditTooLowPublish();
    }

    const images = data.images || [];
    if (images.length > MAX_IMAGES) {
      const { tooManyImages } = require('../utils/errors');
      throw tooManyImages();
    }

    // 售价不能高于原价（前端校验可被绕过，后端必须兜底）
    if (parseFloat(data.price) > parseFloat(data.original_price)) {
      throw badRequest('售价不能高于原价');
    }

    // 敏感词过滤
    const sensitiveFields = ['title', 'description', 'trade_location'];
    for (const field of sensitiveFields) {
      if (data[field]) {
        const check = sensitiveFilter.check(data[field]);
        if (check.hasSensitive) {
          const { sensitiveWord } = require('../utils/errors');
          throw sensitiveWord();
        }
      }
    }

    const product = await productRepo.create({
      seller_id: sellerId,
      title: data.title,
      description: data.description || null,
      category: data.category,
      condition: data.condition,
      original_price: data.original_price,
      price: data.price,
      trade_location: data.trade_location,
      negotiable: data.negotiable !== false,
      images,
    });

    // 缓存失效：新商品发布后清除全部列表缓存（filter 组合 N 种，全部清除最安全）
    cache.deleteByPrefix('products:list:');

    logger.info('商品发布', { productId: product.id, userId: sellerId });

    return nestSeller(product);
  },

  /**
   * 编辑商品（仅发布者可编辑，sold/frozen 状态不可编辑）
   * @param {number} productId
   * @param {number} userId
   * @param {Object} updates
   * @returns {Promise<Object>}
   */
  async update(productId, userId, updates) {
    const product = await productRepo.findById(productId);
    if (!product) {
      throw notFound('商品');
    }
    if (product.seller_id !== userId) {
      const { notOwner } = require('../utils/errors');
      throw notOwner();
    }

    // sold / frozen / deleted 状态不可编辑
    if (['sold', 'frozen', 'deleted'].includes(product.status)) {
      throw invalidStatus('商品');
    }

    // 售价不能高于原价（考虑部分更新：未传字段沿用旧值）
    const effectivePrice = updates.price !== undefined
      ? parseFloat(updates.price) : parseFloat(product.price);
    const effectiveOriginal = updates.original_price !== undefined
      ? parseFloat(updates.original_price) : parseFloat(product.original_price);
    if (effectivePrice > effectiveOriginal) {
      throw badRequest('售价不能高于原价');
    }

    if (updates.images && updates.images.length > MAX_IMAGES) {
      const { tooManyImages } = require('../utils/errors');
      throw tooManyImages();
    }

    // 敏感词过滤
    const sensitiveFields = ['title', 'description', 'trade_location'];
    for (const field of sensitiveFields) {
      if (updates[field]) {
        const check = sensitiveFilter.check(updates[field]);
        if (check.hasSensitive) {
          const { sensitiveWord } = require('../utils/errors');
          throw sensitiveWord();
        }
      }
    }

    const updated = await productRepo.update(productId, updates);

    // 缓存失效：商品详情 + 全部列表缓存
    cache.delete(`product:${productId}`);
    cache.deleteByPrefix('products:list:');

    logger.info('商品编辑', { productId, userId });

    return nestSeller(updated);
  },

  /**
   * 删除商品（软删除：status → deleted）
   * 仅当商品状态为 active 时可删除。
   * 使用事务 + SELECT ... FOR UPDATE 防止 TOCTOU 竞态条件。
   * @param {number} productId
   * @param {number} userId
   * @returns {Promise<void>}
   */
  async delete(productId, userId) {
    await db.transaction(async (conn) => {
      // SELECT ... FOR UPDATE 锁定行，防止并发读取后写覆盖
      const product = await productRepo.findById(productId, conn);
      if (!product) {
        throw notFound('商品');
      }
      if (product.seller_id !== userId) {
        const { notOwner } = require('../utils/errors');
        throw notOwner();
      }
      if (product.status !== 'active') {
        throw invalidStatus('商品');
      }

      await productRepo.updateStatus(productId, 'deleted', conn);

      logger.info('商品删除', { productId, userId });
    });

    // 缓存失效（事务成功后执行，cache 非事务性，与 DB 最终一致）
    cache.delete(`product:${productId}`);
    cache.deleteByPrefix('products:list:');
  },

  /**
   * 我发布的商品列表
   * @param {number} sellerId
   * @param {Object} filters
   * @returns {Promise<{list: Array, total: number}>}
   */
  async findBySeller(sellerId, filters) {
    const result = await productRepo.findBySeller(sellerId, filters);
    // 统一响应格式：与 list() 一致，包含 cover_image 和 seller 字段
    result.list = result.list.map((row) => ({
      ...row,
      seller: null, // 自己的商品不需要卖家信息
      cover_image: extractCoverImage(row.images),
    }));
    return result;
  },
};

module.exports = productService;
