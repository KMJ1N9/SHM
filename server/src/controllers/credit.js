/**
 * 信誉分控制器
 */

const creditService = require('../services/credit');

const creditController = {
  /**
   * GET /api/credit — 我的信誉分 + 变动记录
   */
  async my(req, res, next) {
    try {
      const result = await creditService.my(req.user.id, {
        page: req.query.page,
        pageSize: req.query.pageSize,
      });
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  /**
   * GET /api/users/:id/credit — 查看某用户信誉分（公开）
   */
  async userPublic(req, res, next) {
    try {
      const result = await creditService.userPublic(parseInt(req.params.id, 10));
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },
};

module.exports = creditController;
