/**
 * 商品服务
 *
 * 处理商品列表、详情、发布、编辑、删除（软删除）、我发布的。
 */

const config = require('../config');
const productRepo = require('../repository/product');
const {
  notFound, invalidStatus, creditTooLowPublish, invalidPagination,
} = require('../utils/errors');

const MAX_IMAGES = 6;
const MAX_PAGE_SIZE = 50;

const productService = {
  /**
   * 商品列表（首页瀑布流 + 搜索 + 筛选 + 排序）
   * @param {Object} filters
   * @returns {Promise<{list: Array, total: number, page: number, pageSize: number}>}
   */
  async list(filters) {
    // 分页边界校验
    const page = Math.max(1, parseInt(filters.page, 10) || 1);
    const pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, parseInt(filters.pageSize, 10) || 20));

    if (isNaN(page) || isNaN(pageSize)) {
      throw invalidPagination();
    }

    return productRepo.list({ ...filters, page, pageSize });
  },

  /**
   * 商品详情
   * @param {number} id
   * @returns {Promise<Object>}
   */
  async detail(id) {
    const product = await productRepo.findById(id);
    if (!product) {
      throw notFound('商品');
    }
    return product;
  },

  /**
   * 发布商品
   * @param {number} sellerId
   * @param {number} creditScore - 当前用户信誉分
   * @param {Object} data
   * @returns {Promise<Object>}
   */
  async create(sellerId, creditScore, data) {
    // 信誉分阈值检查
    if (creditScore < config.credit.publishThreshold) {
      throw creditTooLowPublish();
    }

    // 图片数量检查
    const images = data.images || [];
    if (images.length > MAX_IMAGES) {
      const { tooManyImages } = require('../utils/errors');
      throw tooManyImages();
    }

    return productRepo.create({
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
  },

  /**
   * 编辑商品（仅发布者可编辑）
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

    // 图片数量检查
    if (updates.images && updates.images.length > MAX_IMAGES) {
      const { tooManyImages } = require('../utils/errors');
      throw tooManyImages();
    }

    return productRepo.update(productId, updates);
  },

  /**
   * 删除商品（软删除：status → deleted）
   * 仅当商品状态为 active 时可删除
   * @param {number} productId
   * @param {number} userId
   * @returns {Promise<void>}
   */
  async delete(productId, userId) {
    const product = await productRepo.findById(productId);
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

    await productRepo.updateStatus(productId, 'deleted');
  },

  /**
   * 我发布的商品列表
   * @param {number} sellerId
   * @param {Object} filters
   * @returns {Promise<{list: Array, total: number}>}
   */
  async findBySeller(sellerId, filters) {
    return productRepo.findBySeller(sellerId, filters);
  },
};

module.exports = productService;
