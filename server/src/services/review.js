/**
 * 评价服务
 *
 * 处理评价创建、评价列表查询。
 *
 * 评价规则：
 * - 一个订单对同一个人只能评价一次（UNIQUE key 保证）
 * - 三维评分：沟通态度、守时程度、描述一致度，各 1-5 分
 * - 平均分 ≥ 4 = 好评（被评价方信誉分 +1）
 * - 平均分 ≤ 2 = 差评（被评价方信誉分 -5）
 */

const reviewRepo = require('../repository/review');
const orderRepo = require('../repository/order');
const userRepo = require('../repository/user');
const {
  notFound, invalidStatus, orderStateInvalid, duplicateReport,
} = require('../utils/errors');

const reviewService = {
  /**
   * 创建评价
   * @param {number} reviewerId - 评价人 ID
   * @param {Object} data - { order_id, reviewee_id, communication_score, punctuality_score, accuracy_score, comment? }
   * @returns {Promise<Object>}
   */
  async create(reviewerId, data) {
    // 验证订单
    const order = await orderRepo.findById(data.order_id);
    if (!order) {
      throw notFound('订单');
    }
    if (order.status !== 'completed') {
      throw orderStateInvalid('仅已完成订单可评价');
    }

    // 验证评价人参与了该订单
    if (order.buyer_id !== reviewerId && order.seller_id !== reviewerId) {
      throw orderStateInvalid('你未参与该订单，无法评价');
    }

    // 验证被评价人参与了该订单
    if (order.buyer_id !== data.reviewee_id && order.seller_id !== data.reviewee_id) {
      throw orderStateInvalid('被评价人未参与该订单');
    }

    // 防重复
    const alreadyExists = await reviewRepo.exists(data.order_id, reviewerId, data.reviewee_id);
    if (alreadyExists) {
      throw duplicateReport('已对该用户评价过');
    }

    const review = await reviewRepo.create({
      order_id: data.order_id,
      reviewer_id: reviewerId,
      reviewee_id: data.reviewee_id,
      communication_score: data.communication_score,
      punctuality_score: data.punctuality_score,
      accuracy_score: data.accuracy_score,
      comment: data.comment || null,
    });

    // 计算平均分并联动信誉分
    const avgScore = Math.round(
      (data.communication_score + data.punctuality_score + data.accuracy_score) / 3
    );

    const config = require('../config');
    if (avgScore >= 4) {
      // 好评：被评价方 +1
      await userRepo.updateCreditScore(
        data.reviewee_id,
        config.credit.rewardPositiveReview,
        config.credit.max
      );
    } else if (avgScore <= 2) {
      // 差评：被评价方 -5
      await userRepo.updateCreditScore(
        data.reviewee_id,
        -config.credit.deductNegativeReview,
        config.credit.max
      );
    }

    return review;
  },

  /**
   * 获取某订单的所有评价
   * @param {number} orderId
   * @returns {Promise<Array>}
   */
  async listByOrder(orderId) {
    return reviewRepo.findByOrder(orderId);
  },

  /**
   * 获取某用户的评价列表（含聚合统计）
   * @param {number} userId
   * @param {Object} pagination
   * @returns {Promise<{summary: Object, list: Array, total: number}>}
   */
  async listByUser(userId, pagination) {
    return reviewRepo.findByReviewee(userId, pagination);
  },
};

module.exports = reviewService;
