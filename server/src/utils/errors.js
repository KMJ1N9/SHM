/**
 * 错误码工厂函数
 *
 * 所有业务错误必须通过本模块的工厂函数创建，禁止在业务代码中
 * 直接 new AppError() 并手写错误码数字。
 *
 * 错误码分段：
 *   1xxx — 认证与授权
 *   2xxx — 资源
 *   3xxx — 业务状态冲突
 *   4xxx — 输入与风控
 *   5xxx — 权限
 *   6xxx — 系统与第三方
 */

const AppError = require('./app-error');

module.exports = {
  // ============================================================
  // 1xxx — 认证与授权
  // ============================================================

  /** 未登录：Token 缺失或格式错误 */
  unauthenticated: (msg = '请先登录') =>
    new AppError(1001, 401, msg),

  /** Token 过期 / 签名无效 / 被篡改 */
  tokenExpired: (msg = '登录已过期，请重新登录') =>
    new AppError(1002, 401, msg),

  /** token_version 变更——管理员封禁触发所有旧 Token 失效 */
  tokenVersionMismatch: (msg = '账号已在其他设备登录，请重新登录') =>
    new AppError(1003, 401, msg),

  /** 账号已被封禁 */
  accountBanned: (msg = '账号已被限制使用') =>
    new AppError(1004, 403, msg),

  /** 越权：非资源所有者尝试操作他人资源 */
  notOwner: (msg = '您没有权限操作该资源') =>
    new AppError(1005, 403, msg),

  // ============================================================
  // 2xxx — 资源
  // ============================================================

  /** 资源不存在 */
  notFound: (resource = '资源') =>
    new AppError(2001, 404, `${resource}不存在`),

  /** 资源状态不允许当前操作 */
  invalidStatus: (resource = '资源') =>
    new AppError(2002, 422, `${resource}状态不允许此操作`),

  /** 分页参数无效 */
  invalidPagination: (msg = '分页参数无效') =>
    new AppError(2004, 400, msg),

  // ============================================================
  // 3xxx — 业务状态冲突
  // ============================================================

  /** 订单状态不允许此操作（状态机非法转换） */
  orderStateInvalid: (msg = '订单状态不允许此操作') =>
    new AppError(3001, 409, msg),

  /** 工单状态不允许此操作 */
  ticketStateInvalid: (msg = '工单状态不允许此操作') =>
    new AppError(3002, 409, msg),

  /** 商品已被他人锁定（status 非 active） */
  productLocked: (msg = '商品已被他人锁定') =>
    new AppError(3004, 409, msg),

  /** 不能购买自己发布的商品 */
  cannotBuyOwn: (msg = '不能购买自己发布的商品') =>
    new AppError(3005, 409, msg),

  /** 同一订单已有进行中的举报 */
  duplicateReport: (msg = '已存在进行中的举报') =>
    new AppError(3006, 409, msg),

  // ============================================================
  // 4xxx — 输入与风控
  // ============================================================

  /** 请求参数不完整或格式错误 */
  badRequest: (msg = '请求参数不完整或格式错误', detail) =>
    new AppError(4001, 400, msg, detail),

  /** 字段格式不正确 */
  invalidField: (msg, detail) =>
    new AppError(4002, 400, msg, detail),

  /** 图片数量超过限制 */
  tooManyImages: (msg = '图片数量超过限制（最多 6 张）') =>
    new AppError(4003, 400, msg),

  /** 单张图片大小超过限制 */
  imageTooLarge: (maxMB = 5) =>
    new AppError(4004, 400, `单张图片大小超过 ${maxMB}MB`),

  /** 不支持的图片格式 */
  unsupportedImage: (msg = '不支持的图片格式') =>
    new AppError(4005, 400, msg),

  /** 全局限流 */
  rateLimited: (msg = '操作过于频繁，请稍后再试') =>
    new AppError(4006, 429, msg),

  /** 敏感接口限流 */
  rateLimitedSensitive: (retryAfter) =>
    new AppError(4007, 429, `操作过于频繁，请 ${retryAfter} 秒后再试`),

  /** 信誉分不足，无法发布商品 */
  creditTooLowPublish: (msg = '信誉分不足，无法发布商品') =>
    new AppError(4008, 403, msg),

  /** 信誉分不足，无法参与交易 */
  creditTooLowTrade: (msg = '信誉分不足，无法参与交易') =>
    new AppError(4009, 403, msg),

  // ============================================================
  // 5xxx — 权限
  // ============================================================

  /** 需要客服权限 */
  needCS: (msg = '需要客服权限') =>
    new AppError(5001, 403, msg),

  /** 需要管理员权限 */
  needAdmin: (msg = '需要管理员权限') =>
    new AppError(5002, 403, msg),

  /** 不可操作管理员 */
  cannotOperateAdmin: (msg = '不可对管理员执行此操作') =>
    new AppError(5003, 403, msg),

  // ============================================================
  // 6xxx — 系统与第三方
  // ============================================================

  /** COS 上传异常 */
  uploadFailed: (msg = '文件上传服务异常，请稍后重试', detail) =>
    new AppError(6001, 502, msg, detail),

  /** 敏感词拦截 */
  sensitiveWord: (msg = '内容包含违规信息，请修改后重试') =>
    new AppError(6002, 400, msg),

  /** 微信服务异常 */
  wechatAPIFailed: (msg = '微信服务异常，请稍后重试', detail) =>
    new AppError(6003, 502, msg, detail),

  /** IM 服务异常 */
  imAPIFailed: (msg = '消息服务异常，请稍后重试', detail) =>
    new AppError(6004, 502, msg, detail),

  /** 服务器内部错误（兜底） */
  internal: (msg = '服务器内部错误，请稍后重试', detail) =>
    new AppError(6999, 500, msg, detail),
};
