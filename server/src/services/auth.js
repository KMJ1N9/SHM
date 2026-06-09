/**
 * 认证服务
 *
 * 处理微信手机号登录、Token 刷新、获取当前用户信息。
 */

const jwt = require('jsonwebtoken');
const config = require('../config');
const userRepo = require('../repository/user');
const { wechatAPIFailed, tokenExpired, accountBanned } = require('../utils/errors');
const logger = require('../utils/logger').business;

function generateAccessToken(user) {
  return jwt.sign(
    { sub: user.id, role: user.role, tv: user.token_version },
    config.jwt.accessSecret,
    { expiresIn: config.jwt.accessExpires }
  );
}

function generateRefreshToken(user) {
  return jwt.sign(
    { sub: user.id, tv: user.token_version, type: 'refresh' },
    config.jwt.refreshSecret,
    { expiresIn: config.jwt.refreshExpires }
  );
}

const authService = {
  /**
   * 微信手机号登录
   *
   * 流程：前端调用 wx.getPhoneNumber 获取 code →
   *       服务端用 code 换取手机号 →
   *       新用户自动注册 / 老用户直接登录 →
   *       签发 JWT 双 Token
   *
   * @param {string} code - 微信 getPhoneNumber 返回的 code
   * @returns {Promise<{accessToken: string, refreshToken: string, user: Object}>}
   */
  async login(code) {
    // 调用微信 API 换取手机号
    let phone;
    try {
      // TODO: Phase 2 — 替换为真实微信 API 调用
      // 当前 MVP 阶段使用 mock 数据快速跑通流程
      if (process.env.NODE_ENV === 'development' && code.startsWith('mock_')) {
        phone = code.replace('mock_', '');
      } else {
        // 真实微信 API 调用（待微信开放平台审核通过后启用）
        const axios = require('axios');
        const wxResp = await axios.post(
          `https://api.weixin.qq.com/sns/jscode2session?appid=${config.wx.appId}&secret=${config.wx.appSecret}&js_code=${code}&grant_type=authorization_code`
        );
        if (wxResp.data.errcode) {
          logger.error('微信 API 错误', { errcode: wxResp.data.errcode, errmsg: wxResp.data.errmsg });
          throw wechatAPIFailed('获取手机号失败，请稍后重试', wxResp.data);
        }
        // 注：getPhoneNumber 返回的是手机号，非 openid，此处为简化流程
        phone = wxResp.data.phone_info?.purePhoneNumber;
        if (!phone) {
          throw wechatAPIFailed('未获取到手机号，请重新授权');
        }
      }
    } catch (err) {
      if (err.code && err.code >= 6000) throw err; // 已是 AppError，直接抛出
      logger.error('微信登录失败', { error: err.message });
      throw wechatAPIFailed('登录失败，请稍后重试');
    }

    // 查找或创建用户
    let isNewUser = false;
    let user = await userRepo.findByPhone(phone);
    if (!user) {
      user = await userRepo.create({ phone, nickname: '微信用户' });
      isNewUser = true;
      logger.info('新用户注册', { userId: user.id, phone });
    }

    // 检查封禁状态
    if (user.status === 'banned') {
      throw accountBanned();
    }

    // 签发双 Token
    const accessToken = generateAccessToken(user);
    const refreshToken = generateRefreshToken(user);

    return {
      accessToken,
      refreshToken,
      isNewUser,
      user: {
        id: user.id,
        phone: user.phone,
        nickname: user.nickname,
        avatar: user.avatar,
        class_name: user.class_name,
        dorm_building: user.dorm_building,
        role: user.role,
        credit_score: user.credit_score,
      },
    };
  },

  /**
   * 刷新 Access Token
   * @param {string} refreshToken
   * @returns {Promise<{accessToken: string, refreshToken: string}>}
   */
  async refresh(refreshToken) {
    let payload;
    try {
      payload = jwt.verify(refreshToken, config.jwt.refreshSecret);
    } catch {
      throw tokenExpired('登录已过期，请重新登录');
    }

    if (payload.type !== 'refresh') {
      throw tokenExpired('无效的刷新令牌');
    }

    const user = await userRepo.findById(payload.sub);
    if (!user) {
      throw tokenExpired('用户不存在');
    }
    if (user.status === 'banned') {
      throw accountBanned();
    }
    if (user.token_version !== payload.tv) {
      throw tokenExpired('账号已在其他设备登录，请重新登录');
    }

    const newAccessToken = generateAccessToken(user);
    const newRefreshToken = generateRefreshToken(user);

    return {
      accessToken: newAccessToken,
      refreshToken: newRefreshToken,
    };
  },

  /**
   * 获取当前用户信息
   * @param {number} userId
   * @returns {Promise<Object>}
   */
  async me(userId) {
    const user = await userRepo.findById(userId);
    if (!user) {
      throw tokenExpired('用户不存在');
    }
    return {
      id: user.id,
      phone: user.phone,
      nickname: user.nickname,
      avatar: user.avatar,
      class_name: user.class_name,
      dorm_building: user.dorm_building,
      role: user.role,
      credit_score: user.credit_score,
    };
  },
};

module.exports = authService;
