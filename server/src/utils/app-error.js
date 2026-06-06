/**
 * 统一业务异常类
 *
 * 所有业务逻辑层（Service / Repository）抛出此异常，
 * 由 error-handler 中间件统一转换为 JSON 响应。
 *
 * 使用方式：
 *   throw new AppError(2001, 404, '商品不存在', { productId: 1 });
 *
 * @class AppError
 * @extends Error
 */
class AppError extends Error {
  /**
   * @param {number} code       - 错误码（4 位数字，见 errors.js）
   * @param {number} httpStatus - HTTP 状态码
   * @param {string} message    - 人类可读错误描述（展示给用户）
   * @param {*}      [detail=null] - 调试信息（仅 development 环境返回给客户端）
   */
  constructor(code, httpStatus, message, detail = null) {
    super(message);
    this.name = 'AppError';
    this.code = code;
    this.httpStatus = httpStatus;
    this.detail = detail;

    // 确保 instanceof 在跨模块场景下正常工作
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, AppError);
    }
  }
}

module.exports = AppError;
