/**
 * 全局错误处理中间件（4 参数签名）
 *
 * Express 根据参数个数 (err, req, res, next) 识别此中间件为
 * error-handling middleware。必须放在所有路由注册之后。
 *
 * 处理策略：
 *   1. AppError（已知业务异常）→ 结构化 JSON 返回
 *   2. Joi ValidationError → 转为 4001
 *   3. JSON 解析失败 → 转为 4001
 *   4. 未知异常 → 记录完整堆栈到 winston error 日志，返回 6999
 *
 * 安全：production 环境下 detail 字段为 null，不泄露堆栈信息。
 */

const AppError = require('../utils/app-error');
const logger = require('../utils/logger');

/**
 * 全局错误处理中间件
 *
 * @param {Error}   err  - 抛出的异常
 * @param {Request} req  - Express 请求对象
 * @param {Response} res - Express 响应对象
 * @param {Function} _next - 不使用（Express 要求 4 参数）
 */
// eslint-disable-next-line no-unused-vars
function errorHandler(err, req, res, _next) {
  const isDev = process.env.NODE_ENV === 'development';

  // ---- 1. 已知业务异常 ----
  if (err instanceof AppError) {
    if (err.httpStatus >= 500) {
      logger.error.error({
        message: err.message,
        code: err.code,
        stack: err.stack,
        url: req.originalUrl,
        method: req.method,
      });
    }

    return res.status(err.httpStatus).json({
      code: err.code,
      message: err.message,
      detail: isDev ? err.detail : null,
    });
  }

  // ---- 2. Joi 参数校验失败 ----
  if (err.name === 'ValidationError' && err.details) {
    const detail = err.details.map((d) => d.message).join('; ');
    return res.status(400).json({
      code: 4001,
      message: '请求参数不完整或格式错误',
      detail: isDev ? detail : null,
    });
  }

  // ---- 3. JSON 解析失败 ----
  if (err.type === 'entity.parse.failed') {
    return res.status(400).json({
      code: 4001,
      message: '请求体 JSON 格式错误',
      detail: isDev ? err.message : null,
    });
  }

  // ---- 4. 未知异常 ----
  logger.error.error({
    message: err.message,
    stack: err.stack,
    url: req.originalUrl,
    method: req.method,
    body: isDev ? req.body : undefined,
  });

  return res.status(500).json({
    code: 6999,
    message: '服务器内部错误，请稍后重试',
    detail: isDev ? err.message : null,
  });
}

module.exports = errorHandler;
