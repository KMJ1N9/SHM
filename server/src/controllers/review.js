/**
 * 评价控制器
 */

const reviewService = require('../services/review');
const { badRequest } = require('../utils/errors');

const reviewController = {
  /**
   * POST /api/reviews — 创建评价
   */
  async create(req, res, next) {
    try {
      const review = await reviewService.create(req.user.id, req.body);
      res.status(201).json({ code: 0, message: 'ok', data: review });
    } catch (err) {
      next(err);
    }
  },

  /**
   * GET /api/reviews — 评价列表
   * query: order_id → 该订单的评价；user_id → 该用户的评价
   */
  async list(req, res, next) {
    try {
      if (req.query.order_id) {
        const reviews = await reviewService.listByOrder(parseInt(req.query.order_id, 10));
        return res.json({ code: 0, message: 'ok', data: { list: reviews } });
      }
      if (req.query.user_id) {
        const result = await reviewService.listByUser(parseInt(req.query.user_id, 10), {
          page: req.query.page,
          pageSize: req.query.pageSize,
        });
        return res.json({ code: 0, message: 'ok', data: result });
      }
      throw badRequest('请提供 order_id 或 user_id');
    } catch (err) {
      next(err);
    }
  },
};

module.exports = reviewController;
