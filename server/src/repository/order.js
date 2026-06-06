/**
 * 订单数据访问层
 *
 * 封装所有 orders 表的 SQL 操作。
 * 支持事务：下单需同时更新 products.status 和写入 orders。
 */

const { query, transaction } = require('../models/db');
const { notFound, productLocked, orderStateInvalid } = require('../utils/errors');

const ORDER_FIELDS = [
  'id', 'product_id', 'buyer_id', 'seller_id', 'status',
  'cancelled_by', 'idempotent_key', 'product_snapshot',
  'met_at', 'confirmed_at', 'created_at', 'updated_at',
].join(', ');

const orderRepo = {
  /**
   * 根据 ID 查找订单
   * @param {number} id
   * @returns {Promise<Object|null>}
   */
  async findById(id) {
    const [row] = await query(
      `SELECT ${ORDER_FIELDS} FROM orders WHERE id = ?`,
      [id]
    );
    return row || null;
  },

  /**
   * 根据幂等键查找已有订单
   * @param {string} idempotentKey - `${buyer_id}_${product_id}`
   * @returns {Promise<Object|null>}
   */
  async findByIdempotentKey(idempotentKey) {
    const [row] = await query(
      `SELECT ${ORDER_FIELDS} FROM orders WHERE idempotent_key = ?`,
      [idempotentKey]
    );
    return row || null;
  },

  /**
   * 创建订单（事务：更新商品状态 + 写入订单）
   * @param {Object} data - { product_id, buyer_id, seller_id, product_snapshot }
   * @returns {Promise<Object>}
   */
  async create(data) {
    const idempotentKey = `${data.buyer_id}_${data.product_id}`;

    return transaction(async (conn) => {
      // 锁定商品行，防止并发下单
      const [products] = await conn.execute(
        'SELECT status FROM products WHERE id = ? FOR UPDATE',
        [data.product_id]
      );
      if (products.length === 0) {
        throw notFound('商品');
      }
      if (products[0].status !== 'active') {
        throw productLocked();
      }

      // 更新商品状态
      await conn.execute(
        'UPDATE products SET status = ? WHERE id = ?',
        ['reserved', data.product_id]
      );

      // 写入订单
      const [result] = await conn.execute(
        `INSERT INTO orders (product_id, buyer_id, seller_id, idempotent_key, product_snapshot)
         VALUES (?, ?, ?, ?, ?)`,
        [
          data.product_id, data.buyer_id, data.seller_id,
          idempotentKey, JSON.stringify(data.product_snapshot),
        ]
      );

      const [order] = await conn.execute(
        `SELECT ${ORDER_FIELDS} FROM orders WHERE id = ?`,
        [result.insertId]
      );

      return order[0];
    });
  },

  /**
   * 更新订单状态
   * @param {number} id
   * @param {string} status
   * @param {Object} extra - 额外更新字段 { cancelled_by?, met_at?, confirmed_at? }
   * @returns {Promise<Object>}
   */
  async updateStatus(id, status, extra = {}) {
    const sets = ['status = ?'];
    const params = [status];

    if (extra.cancelled_by) {
      sets.push('cancelled_by = ?');
      params.push(extra.cancelled_by);
    }
    if (extra.met_at) {
      sets.push('met_at = ?');
      params.push(extra.met_at);
    }
    if (extra.confirmed_at) {
      sets.push('confirmed_at = ?');
      params.push(extra.confirmed_at);
    }

    params.push(id);
    await query(
      `UPDATE orders SET ${sets.join(', ')} WHERE id = ?`,
      params
    );
    return this.findById(id);
  },

  /**
   * 我的订单列表（支持角色和状态筛选）
   * @param {number} userId
   * @param {Object} filters - { role?, status?, page, pageSize }
   * @returns {Promise<{list: Array, total: number}>}
   */
  async findByUser(userId, filters = {}) {
    const { role = 'all', status = 'all', page = 1, pageSize = 20 } = filters;
    const conditions = [];
    const params = [];

    if (role === 'buyer') {
      conditions.push('o.buyer_id = ?');
      params.push(userId);
    } else if (role === 'seller') {
      conditions.push('o.seller_id = ?');
      params.push(userId);
    } else {
      conditions.push('(o.buyer_id = ? OR o.seller_id = ?)');
      params.push(userId, userId);
    }

    if (status && status !== 'all') {
      conditions.push('o.status = ?');
      params.push(status);
    }

    const where = `WHERE ${conditions.join(' AND ')}`;

    const [countResult] = await query(
      `SELECT COUNT(*) AS total FROM orders o ${where}`,
      params
    );

    const offset = (page - 1) * pageSize;
    const rows = await query(
      `SELECT o.id, o.product_id, o.buyer_id, o.seller_id, o.status,
              o.cancelled_by, o.product_snapshot, o.met_at, o.confirmed_at,
              o.created_at, o.updated_at,
              buyer.nickname AS buyer_nickname, buyer.avatar AS buyer_avatar,
              seller.nickname AS seller_nickname, seller.avatar AS seller_avatar
       FROM orders o
       JOIN users buyer ON o.buyer_id = buyer.id
       JOIN users seller ON o.seller_id = seller.id
       ${where}
       ORDER BY o.updated_at DESC
       LIMIT ? OFFSET ?`,
      [...params, pageSize, offset]
    );

    return { list: rows, total: countResult.total };
  },

  /**
   * 查找超时未面交的订单（pending 超过 7 天）
   * 供定时任务使用
   * @returns {Promise<Array>}
   */
  async findTimeoutPending() {
    const rows = await query(
      `SELECT id, product_id FROM orders
       WHERE status = 'pending' AND created_at < DATE_SUB(NOW(), INTERVAL 7 DAY)`
    );
    return rows;
  },

  /**
   * 查找超时未确认收货的订单（met 超过 3 天）
   * 供定时任务使用
   * @returns {Promise<Array>}
   */
  async findTimeoutMet() {
    const rows = await query(
      `SELECT id, product_id FROM orders
       WHERE status = 'met' AND met_at < DATE_SUB(NOW(), INTERVAL 3 DAY)`
    );
    return rows;
  },
  /**
   * 确认收货（事务：订单→completed + 商品→sold，原子操作）
   *
   * 使用 FOR UPDATE 行锁防止并发修改，事务内二次校验状态。
   *
   * @param {number} id - 订单 ID
   * @returns {Promise<Object>}
   */
  async confirmOrder(id) {
    return transaction(async (conn) => {
      const [[order]] = await conn.execute(
        `SELECT id, product_id, status FROM orders WHERE id = ? FOR UPDATE`,
        [id]
      );
      if (!order) {
        throw notFound('订单');
      }
      if (order.status !== 'met') {
        throw orderStateInvalid('仅已面交状态的订单可确认收货');
      }

      await conn.execute(
        'UPDATE orders SET status = ?, confirmed_at = NOW() WHERE id = ?',
        ['completed', id]
      );
      await conn.execute(
        'UPDATE products SET status = ? WHERE id = ?',
        ['sold', order.product_id]
      );

      const [[updated]] = await conn.execute(
        `SELECT ${ORDER_FIELDS} FROM orders WHERE id = ?`,
        [id]
      );
      return updated;
    });
  },

  /**
   * 取消订单（事务：订单→cancelled + 商品→active，原子操作）
   *
   * 使用 FOR UPDATE 行锁防止并发修改，事务内二次校验状态。
   *
   * @param {number} id - 订单 ID
   * @param {string} cancelledBy - 'buyer' | 'seller'
   * @returns {Promise<Object>}
   */
  async cancelOrder(id, cancelledBy) {
    return transaction(async (conn) => {
      const [[order]] = await conn.execute(
        `SELECT id, product_id, status FROM orders WHERE id = ? FOR UPDATE`,
        [id]
      );
      if (!order) {
        throw notFound('订单');
      }

      // 事务内二次校验状态（防 TOCTOU）
      if (order.status === 'pending') {
        // pending：买卖双方均可取消
      } else if (order.status === 'met' && cancelledBy === 'buyer') {
        // met：仅买家可取消
      } else {
        throw orderStateInvalid('当前订单状态不可取消');
      }

      await conn.execute(
        'UPDATE orders SET status = ?, cancelled_by = ? WHERE id = ?',
        ['cancelled', cancelledBy, id]
      );
      await conn.execute(
        'UPDATE products SET status = ? WHERE id = ?',
        ['active', order.product_id]
      );

      const [[updated]] = await conn.execute(
        `SELECT ${ORDER_FIELDS} FROM orders WHERE id = ?`,
        [id]
      );
      return updated;
    });
  },
};

module.exports = orderRepo;
