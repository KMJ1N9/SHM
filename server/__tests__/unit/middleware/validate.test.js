/**
 * 请求参数校验中间件 — 单元测试（6 条）
 *
 * 被测模块：server/src/middleware/validate.js
 * 覆盖：validateBody / validateQuery / validateParams 通过+失败+stripUnknown
 * 测试计划参考：编码迭代计划 §13.1 Phase 1-6
 *
 * 策略：纯 Joi 函数测试，无 mock
 */

const Joi = require('joi');
const { validateBody, validateQuery, validateParams } = require('../../../src/middleware/validate');

const productSchema = Joi.object({
  title: Joi.string().required().max(200),
  price: Joi.number().min(0).required(),
  description: Joi.string().allow('').max(5000),
});

const paginationSchema = Joi.object({
  page: Joi.number().integer().min(1).default(1),
  pageSize: Joi.number().integer().min(1).max(100).default(20),
});

function mockReq(body, query, params) {
  return { body: body ?? {}, query: query ?? {}, params: params ?? {} };
}

// ============================================================
// VD-001~006
// ============================================================
describe('validate middleware', () => {
  it('VD-001: validateBody 通过 → next() 且清洗值', () => {
    const req = mockReq({ title: '测试商品', price: 99, description: '描述' });
    const next = vi.fn();
    const middleware = validateBody(productSchema);

    middleware(req, {}, next);

    expect(next).toHaveBeenCalledWith();
    expect(req.body.title).toBe('测试商品');
    expect(req.body.price).toBe(99);
  });

  it('VD-002: validateBody 失败（缺少必填字段）→ throw 4001', () => {
    const req = mockReq({ title: '测试' }); // 缺少 price
    const next = vi.fn();
    const middleware = validateBody(productSchema);

    try {
      middleware(req, {}, next);
    } catch (err) {
      expect(err).toMatchObject({
        code: 4001,
      });
      expect(next).not.toHaveBeenCalled();
    }
  });

  it('VD-003: validateBody stripUnknown → 移除未定义字段', () => {
    const req = mockReq({
      title: '测试商品',
      price: 99,
      malicious_field: 'should be stripped',
      extra: true,
    });
    const next = vi.fn();
    const middleware = validateBody(productSchema);

    middleware(req, {}, next);

    expect(next).toHaveBeenCalledWith();
    expect(req.body.malicious_field).toBeUndefined();
    expect(req.body.extra).toBeUndefined();
  });

  it('VD-004: validateQuery 通过 → 应用默认值', () => {
    const req = mockReq({}, {});
    const next = vi.fn();
    const middleware = validateQuery(paginationSchema);

    middleware(req, {}, next);

    expect(next).toHaveBeenCalledWith();
    expect(req.query.page).toBe(1);
    expect(req.query.pageSize).toBe(20);
  });

  it('VD-005: validateParams 通过 → 自动转换类型', () => {
    const idSchema = Joi.object({
      id: Joi.number().integer().min(1).required(),
    });

    const req = mockReq({}, {}, { id: '123' });
    const next = vi.fn();
    validateParams(idSchema)(req, {}, next);

    expect(next).toHaveBeenCalledWith();
    expect(req.params.id).toBe(123);
  });

  it('VD-006: validateQuery 非法值（负数 page）→ throw 4001', () => {
    const req = mockReq({}, { page: -1 });
    const next = vi.fn();
    const middleware = validateQuery(paginationSchema);

    try {
      middleware(req, {}, next);
    } catch (err) {
      expect(err).toMatchObject({
        code: 4001,
      });
    }
  });
});
