/**
 * 请求参数校验中间件工厂
 *
 * 使用 Joi 进行 schema 校验。校验失败返回 4001 + 第一条错误信息。
 *
 * 使用方式：
 *   const { validateBody, validateQuery } = require('../middleware/validate');
 *   const Joi = require('joi');
 *
 *   router.post('/products', auth, validateBody(Joi.object({
 *     title: Joi.string().required().max(200),
 *     price: Joi.number().min(0).required(),
 *   })), controller.create);
 *
 * 校验目标：
 *   - validateBody:   req.body
 *   - validateQuery:  req.query
 *   - validateParams: req.params
 */

const Joi = require('joi');
const errors = require('../utils/errors');

/**
 * 创建校验中间件
 * @param {Joi.Schema} schema - Joi 校验模式
 * @param {'body'|'query'|'params'} source - 校验数据来源
 * @returns {Function} Express 中间件
 */
function createValidator(schema, source) {
  return (req, res, next) => {
    const { error, value } = schema.validate(req[source], {
      abortEarly: true,    // 遇到第一个错误即停止
      stripUnknown: true,  // 移除 schema 未定义的字段
      allowUnknown: false, // 禁止未知字段
    });

    if (error) {
      // Joi 错误信息 → 4001
      const detail = error.details.map((d) => d.message).join('; ');
      throw errors.badRequest(detail, { field: error.details[0]?.path.join('.') });
    }

    // 校验通过，替换为清洗后的值
    req[source] = value;
    next();
  };
}

/** 校验 req.body */
function validateBody(schema) {
  return createValidator(schema, 'body');
}

/** 校验 req.query */
function validateQuery(schema) {
  return createValidator(schema, 'query');
}

/** 校验 req.params */
function validateParams(schema) {
  return createValidator(schema, 'params');
}

module.exports = { validateBody, validateQuery, validateParams };
