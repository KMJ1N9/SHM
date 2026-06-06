/**
 * JWT 鉴权中间件
 *
 * 职责（按顺序）：
 *   1. 从 Authorization header 提取 Bearer token
 *   2. 验证 JWT 签名与过期时间
 *   3. 查询用户记录，校验 token_version（封禁后旧 token 失效）
 *   4. 校验用户状态（banned → 1004）
 *   5. 将用户信息挂载到 req.user
 *
 * 豁免路径（白名单）：
 *   - POST /api/auth/login   — 登录接口
 *   - GET  /api/health        — 健康检查
 */

const jwt = require('jsonwebtoken');
const config = require('../config');
const errors = require('../utils/errors');
const { query } = require('../models/db');

/** 不需要鉴权的路径白名单（路径相对于挂载点 /api） */
const EXEMPT_PATHS = [
  { method: 'POST', path: '/auth/login' },
  { method: 'GET', path: '/health' },
];

/**
 * 检查请求是否在白名单中
 */
function isExempt(method, path) {
  return EXEMPT_PATHS.some(
    (entry) => entry.method === method && entry.path === path
  );
}

/**
 * JWT 鉴权中间件
 */
async function auth(req, res, next) {
  try {
    // 1. 白名单放行
    if (isExempt(req.method, req.path)) {
      return next();
    }

    // 2. 提取 Token
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      throw errors.unauthenticated();
    }
    const token = authHeader.slice(7);

    // 3. 验证 JWT 签名（使用 access token 密钥）
    let payload;
    try {
      payload = jwt.verify(token, config.jwt.accessSecret);
    } catch (jwtErr) {
      if (jwtErr.name === 'TokenExpiredError') {
        throw errors.tokenExpired();
      }
      throw errors.tokenExpired('Token 无效');
    }

    // 4. 查询用户（校验 token_version + 封禁状态）
    const [user] = await query(
      'SELECT id, phone, nickname, avatar, class_name, dorm_building, role, status, token_version, credit_score FROM users WHERE id = ?',
      [payload.sub]
    );

    if (!user) {
      throw errors.tokenExpired('用户不存在');
    }

    // token_version 不匹配 → 所有已签发 token 失效（被踢下线）
    if (user.token_version !== payload.tv) {
      throw errors.tokenVersionMismatch();
    }

    // 账号已封禁
    if (user.status === 'banned') {
      throw errors.accountBanned();
    }

    // 5. 挂载用户信息到 req（后续中间件和控制器使用）
    req.user = {
      id: user.id,
      phone: user.phone,
      nickname: user.nickname,
      avatar: user.avatar,
      class_name: user.class_name,
      dorm_building: user.dorm_building,
      role: user.role,
      status: user.status,
      credit_score: user.credit_score,
    };

    next();
  } catch (err) {
    next(err);
  }
}

module.exports = auth;
