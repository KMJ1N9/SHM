/**
 * 商品控制器
 */

const productService = require('../services/product');

const productController = {
  /**
   * GET /api/products — 商品列表
   */
  async list(req, res, next) {
    try {
      const result = await productService.list(req.query);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },

  /**
   * GET /api/products/:id — 商品详情
   */
  async detail(req, res, next) {
    try {
      const viewer = req.user ? { id: req.user.id, role: req.user.role } : undefined;
      const product = await productService.detail(parseInt(req.params.id, 10), viewer);
      res.json({ code: 0, message: 'ok', data: product });
    } catch (err) {
      next(err);
    }
  },

  /**
   * POST /api/products — 发布商品
   */
  async create(req, res, next) {
    try {
      const product = await productService.create(
        req.user.id,
        req.user.credit_score,
        req.body
      );
      res.status(201).json({ code: 0, message: 'ok', data: product });
    } catch (err) {
      next(err);
    }
  },

  /**
   * PUT /api/products/:id — 编辑商品
   */
  async update(req, res, next) {
    try {
      const product = await productService.update(
        parseInt(req.params.id, 10),
        req.user.id,
        req.body
      );
      res.json({ code: 0, message: 'ok', data: product });
    } catch (err) {
      next(err);
    }
  },

  /**
   * DELETE /api/products/:id — 删除商品（软删除）
   */
  async delete(req, res, next) {
    try {
      await productService.delete(
        parseInt(req.params.id, 10),
        req.user.id
      );
      res.json({ code: 0, message: 'ok', data: null });
    } catch (err) {
      next(err);
    }
  },

  /**
   * GET /api/products/my — 我发布的商品
   */
  async my(req, res, next) {
    try {
      const result = await productService.findBySeller(req.user.id, req.query);
      res.json({ code: 0, message: 'ok', data: result });
    } catch (err) {
      next(err);
    }
  },
};

module.exports = productController;
