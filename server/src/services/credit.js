/**
 * 信誉分服务
 *
 * 处理信誉分查询、变动记录查询、信誉分变动（加分/扣分）。
 */

const config = require('../config');
const userRepo = require('../repository/user');
const creditRepo = require('../repository/credit');
const { notFound } = require('../utils/errors');

const creditService = {
  /**
   * 我的信誉分 + 变动记录
   * @param {number} userId
   * @param {Object} pagination
   * @returns {Promise<{score: number, change_log: Array}>}
   */
  async my(userId, pagination) {
    const userCredit = await creditRepo.findByUserId(userId);
    if (!userCredit) {
      throw notFound('用户');
    }

    const changeLogs = await creditRepo.findChangeLogs(userId, pagination);

    return {
      score: userCredit.score,
      change_log: changeLogs.list,
    };
  },

  /**
   * 查看某用户信誉分（公开）
   * @param {number} userId
   * @returns {Promise<{user_id: number, score: number}>}
   */
  async userPublic(userId) {
    const userCredit = await creditRepo.findByUserId(userId);
    if (!userCredit) {
      throw notFound('用户');
    }

    return {
      user_id: userCredit.user_id,
      score: userCredit.score,
    };
  },

  /**
   * 信誉分变动（内部调用，供其他 service 使用）
   *
   * @param {number} userId - 用户 ID
   * @param {number} delta - 变动值（正+ / 负-）
   * @param {string} reason - 变动原因（中文描述）
   * @param {Object} opts - { refId? } 关联的业务 ID
   * @returns {Promise<{previousScore: number, currentScore: number}>}
   */
  async changeScore(userId, delta, reason, opts = {}) {
    const current = await userRepo.findById(userId);
    if (!current) {
      throw notFound('用户');
    }

    const previousScore = current.credit_score;

    // 原子更新信誉分（有上下限保护）
    await userRepo.updateCreditScore(userId, delta, config.credit.max);

    // 重新查询确认最终值
    const updated = await creditRepo.findByUserId(userId);

    // 写入变动记录到 notifications
    await creditRepo.createChangeLog({
      userId,
      delta,
      reason,
      currentScore: updated.score,
      previousScore,
      refId: opts.refId,
    });

    return {
      previousScore,
      currentScore: updated.score,
    };
  },
};

module.exports = creditService;
