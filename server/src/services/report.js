/**
 * 举报服务
 *
 * 处理举报创建、我的举报列表查询。
 */

const reportRepo = require('../repository/report');
const orderRepo = require('../repository/order');
const productRepo = require('../repository/product');
const {
  notFound, duplicateReport, orderStateInvalid,
} = require('../utils/errors');

const reportService = {
  /**
   * 创建举报
   * @param {number} reporterId
   * @param {Object} data - { reported_user_id, product_id?, order_id?, type, description, evidence_images? }
   * @returns {Promise<Object>}
   */
  async create(reporterId, data) {
    // 举报自己检查
    if (data.reported_user_id === reporterId) {
      throw orderStateInvalid('不能举报自己');
    }

    // 如果关联了订单，检查重复举报
    if (data.order_id) {
      const hasActive = await reportRepo.hasActiveReport(reporterId, data.order_id);
      if (hasActive) {
        throw duplicateReport();
      }

      // 验证举报人参与了该订单
      const order = await orderRepo.findById(data.order_id);
      if (!order) {
        throw notFound('订单');
      }
      if (order.buyer_id !== reporterId && order.seller_id !== reporterId) {
        throw orderStateInvalid('你未参与该订单，无法举报');
      }
    }

    // 如果关联了商品，验证商品存在
    if (data.product_id) {
      const product = await productRepo.findById(data.product_id);
      if (!product) {
        throw notFound('商品');
      }
    }

    return reportRepo.create({
      reporter_id: reporterId,
      reported_user_id: data.reported_user_id,
      product_id: data.product_id || null,
      order_id: data.order_id || null,
      type: data.type,
      description: data.description,
      evidence_images: data.evidence_images || [],
    });
  },

  /**
   * 我的举报列表
   * @param {number} reporterId
   * @param {Object} filters - { page, pageSize }
   * @returns {Promise<{list: Array, total: number}>}
   */
  async list(reporterId, filters) {
    return reportRepo.list({ ...filters, reporter_id: reporterId });
  },
};

module.exports = reportService;
