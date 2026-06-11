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
const creditService = require('./credit');
const imProvider = require('./im/provider');
const logger = require('../utils/logger').business;
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
      return { order: existing, created: false };
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

    // IM 通知卖家：有人想要购买
    imProvider.sendSystemMessage(product.seller_id, {
      title: '有人想要购买你的商品',
      content: `用户已对「${product.title}」发起交易，请查看订单详情`,
      extra: { type: 'order', order_id: order.id, product_id: data.product_id },
    }).catch(err => logger.warn('IM 通知卖家失败', { orderId: order.id, error: err.message }));

    logger.info('订单创建', { orderId: order.id, userId: buyerId, productId: data.product_id });

    return { order, created: true };
  },

  /**
   * 我的订单列表
   *
   * 自动路由分页策略：
   *   filters.cursor 存在 → 游标分页（listByCursor），O(1) 定位
   *   filters.cursor 不存在 → 偏移分页（findByUser），兼容旧调用
   *
   * @param {number} userId
   * @param {Object} filters - { role?, status?, page?, pageSize?, cursor?, limit }
   * @returns {Promise<{list: Array, total: number, cursor?: number, hasMore?: boolean}>}
   */
  async list(userId, filters) {
    if (filters.cursor !== undefined && filters.cursor !== null) {
      return orderRepo.listByCursor({ ...filters, userId });
    }
    // 也支持显式传 cursor=0 或首次请求走游标
    if (filters.limit && !filters.page) {
      return orderRepo.listByCursor({ ...filters, userId });
    }
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

    const updated = await orderRepo.updateStatus(orderId, 'met', { met_at: new Date() });

    // IM 通知对方：已确认面交
    const notifyUserId = order.buyer_id === userId ? order.seller_id : order.buyer_id;
    imProvider.sendSystemMessage(notifyUserId, {
      title: '对方已确认面交',
      content: '请尽快确认收货',
      extra: { type: 'order', order_id: orderId },
    }).catch(err => logger.warn('IM 通知面交失败', { orderId, error: err.message }));

    logger.info('面交确认', { orderId, userId });

    return updated;
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

    // 事务内原子操作：订单→completed + 商品→sold
    // 状态校验 + FOR UPDATE 在 orderRepo.confirmOrder 内部完成
    const confirmed = await orderRepo.confirmOrder(orderId);

    // 卖家信誉分 +2（通过 creditService 确保写入变动通知）
    const sellerId = order.seller_id;
    try {
      await creditService.changeScore(sellerId, config.credit.rewardTransaction, '交易完成奖励', { refId: orderId });
    } catch (err) {
      logger.warn('信誉分更新失败', { orderId, sellerId, error: err.message });
    }

    // IM 通知买卖双方互评
    const reviewMsg = {
      title: '订单已完成',
      content: '买家已确认收货，请互相评价',
      extra: { type: 'review_remind', order_id: orderId },
    };
    imProvider.sendSystemMessage(order.buyer_id, reviewMsg)
      .catch(err => logger.warn('IM 通知买家互评失败', { orderId, error: err.message }));
    imProvider.sendSystemMessage(sellerId, reviewMsg)
      .catch(err => logger.warn('IM 通知卖家互评失败', { orderId, error: err.message }));

    logger.info('订单确认收货', { orderId, userId });

    return confirmed;
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

    // 确定取消方（业务逻辑在 service 层）
    let cancelledBy;
    if (order.status === 'pending') {
      cancelledBy = isBuyer ? 'buyer' : 'seller';
    } else if (order.status === 'met' && isBuyer) {
      cancelledBy = 'buyer';
    } else {
      throw orderStateInvalid('当前订单状态不可取消');
    }

    // 事务内原子操作：订单→cancelled + 商品→active
    // 状态二次校验 + FOR UPDATE 在 orderRepo.cancelOrder 内部完成
    const cancelled = await orderRepo.cancelOrder(orderId, cancelledBy);

    // IM 通知对方：订单已取消
    const notifyUserId = cancelledBy === 'buyer' ? order.seller_id : order.buyer_id;
    imProvider.sendSystemMessage(notifyUserId, {
      title: '订单已取消',
      content: `订单已被${cancelledBy === 'buyer' ? '买家' : '卖家'}取消`,
      extra: { type: 'order', order_id: orderId, status: 'cancelled' },
    }).catch(err => logger.warn('IM 通知取消失败', { orderId, error: err.message }));

    logger.info('订单取消', { orderId, userId, cancelledBy });

    return cancelled;
  },
};

module.exports = orderService;
