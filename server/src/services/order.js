/**
 * 订单服务
 *
 * 处理订单创建、列表、详情、面交确认、收货确认、取消。
 *
 * 订单状态机：
 *   pending → met → completed
 *   pending → cancelled
 *   met → completed (确认收货)
 *
 * 幂等性：${buyer_id}_${product_id} 作为 idempotent_key，
 * 重复创建同一订单时直接返回已有订单。
 */

const config = require('../config');
const orderRepo = require('../repository/order');
const productRepo = require('../repository/product');
const {
  notFound, invalidStatus, orderStateInvalid, productLocked,
  cannotBuyOwn, creditTooLowTrade,
} = require('../utils/errors');

const orderService = {
  /**
   * 创建订单（下单）
   * @param {number} buyerId
   * @param {number} creditScore - 买家信誉分
   * @param {Object} data - { product_id }
   * @returns {Promise<Object>}
   */
  async create(buyerId, creditScore, data) {
    // 信誉分阈值检查
    if (creditScore < config.credit.tradeThreshold) {
      throw creditTooLowTrade();
    }

    // 幂等性检查
    const idempotentKey = `${buyerId}_${data.product_id}`;
    const existing = await orderRepo.findByIdempotentKey(idempotentKey);
    if (existing) {
      return existing;
    }

    // 查询商品信息
    const product = await productRepo.findById(data.product_id);
    if (!product) {
      throw notFound('商品');
    }
    if (product.seller_id === buyerId) {
      throw cannotBuyOwn();
    }
    if (product.status !== 'active') {
      throw productLocked();
    }

    // 构建商品快照
    const productSnapshot = {
      title: product.title,
      description: product.description,
      category: product.category,
      condition: product.condition,
      price: product.price,
      original_price: product.original_price,
      trade_location: product.trade_location,
      images: product.images,
      negotiable: product.negotiable,
      seller_id: product.seller_id,
      seller_nickname: product.seller_nickname,
      seller_avatar: product.seller_avatar,
    };

    // 事务创建订单
    const order = await orderRepo.create({
      product_id: data.product_id,
      buyer_id: buyerId,
      seller_id: product.seller_id,
      product_snapshot: productSnapshot,
    });

    return order;
  },

  /**
   * 我的订单列表
   * @param {number} userId
   * @param {Object} filters - { role?, status?, page, pageSize }
   * @returns {Promise<{list: Array, total: number}>}
   */
  async list(userId, filters) {
    return orderRepo.findByUser(userId, filters);
  },

  /**
   * 订单详情
   * @param {number} orderId
   * @param {number} userId
   * @returns {Promise<Object>}
   */
  async detail(orderId, userId) {
    const order = await orderRepo.findById(orderId);
    if (!order) {
      throw notFound('订单');
    }
    // 仅买方或卖方可查看
    if (order.buyer_id !== userId && order.seller_id !== userId) {
      const { notOwner } = require('../utils/errors');
      throw notOwner();
    }
    return order;
  },

  /**
   * 标记面交（买方或卖方任一方点击"已面交"）
   *
   * 仅 pending 状态可操作。
   * 单方确认即可触发（不要求双方），met 后商品状态保持 reserved。
   * met 超过 3 天未确认收货 → 自动完成（定时任务处理）。
   *
   * @param {number} orderId
   * @param {number} userId
   * @returns {Promise<Object>}
   */
  async markAsMet(orderId, userId) {
    const order = await orderRepo.findById(orderId);
    if (!order) {
      throw notFound('订单');
    }
    if (order.buyer_id !== userId && order.seller_id !== userId) {
      const { notOwner } = require('../utils/errors');
      throw notOwner();
    }
    if (order.status !== 'pending') {
      throw orderStateInvalid('仅待面交状态的订单可标记面交');
    }

    return orderRepo.updateStatus(orderId, 'met', { met_at: new Date() });
  },

  /**
   * 确认收货（仅买家）
   *
   * 仅 met 状态可操作。
   * 确认后：商品 status → sold，触发双方互评入口 + 卖家信誉分 +2。
   *
   * @param {number} orderId
   * @param {number} userId
   * @returns {Promise<Object>}
   */
  async confirm(orderId, userId) {
    const order = await orderRepo.findById(orderId);
    if (!order) {
      throw notFound('订单');
    }
    if (order.buyer_id !== userId) {
      const { notOwner } = require('../utils/errors');
      throw notOwner();
    }
    if (order.status !== 'met') {
      throw orderStateInvalid('仅已面交状态的订单可确认收货');
    }

    // 更新订单状态
    const updatedOrder = await orderRepo.updateStatus(orderId, 'completed', {
      confirmed_at: new Date(),
    });

    // 更新商品状态为 sold
    await productRepo.updateStatus(order.product_id, 'sold');

    return updatedOrder;
  },

  /**
   * 取消订单
   *
   * pending 状态：买家/卖家均可取消。
   * met 状态（已面交但未确认收货）：仅买家可取消。
   * 其他状态不可取消。
   *
   * @param {number} orderId
   * @param {number} userId
   * @returns {Promise<Object>}
   */
  async cancel(orderId, userId) {
    const order = await orderRepo.findById(orderId);
    if (!order) {
      throw notFound('订单');
    }

    const isBuyer = order.buyer_id === userId;
    const isSeller = order.seller_id === userId;

    if (!isBuyer && !isSeller) {
      const { notOwner } = require('../utils/errors');
      throw notOwner();
    }

    // 确定取消方
    let cancelledBy;
    if (order.status === 'pending') {
      cancelledBy = isBuyer ? 'buyer' : 'seller';
    } else if (order.status === 'met' && isBuyer) {
      cancelledBy = 'buyer';
    } else {
      throw orderStateInvalid('当前订单状态不可取消');
    }

    const updatedOrder = await orderRepo.updateStatus(orderId, 'cancelled', { cancelled_by: cancelledBy });

    // 恢复商品状态为 active
    await productRepo.updateStatus(order.product_id, 'active');

    return updatedOrder;
  },
};

module.exports = orderService;
