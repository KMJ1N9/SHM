/**
 * 微信开放平台 API 工具
 *
 * 封装微信服务端 API 调用：
 * - code2Session: 小程序登录凭证校验
 * - getPhoneNumber: 手机号快速验证
 *
 * 文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/
 */

const axios = require('axios');
const config = require('../config');
const { wechatAPIFailed } = require('./errors');
const logger = require('./logger').business;

const WECHAT_BASE = 'https://api.weixin.qq.com';

/**
 * 获取小程序全局 access_token（带缓存）
 *
 * @returns {Promise<string>}
 */
async function getAccessToken() {
  // 使用模块级缓存避免频繁请求
  if (getAccessToken._cached && getAccessToken._cached.expiresAt > Date.now()) {
    return getAccessToken._cached.token;
  }

  try {
    const resp = await axios.get(`${WECHAT_BASE}/cgi-bin/token`, {
      params: {
        grant_type: 'client_credential',
        appid: config.wx.appId,
        secret: config.wx.appSecret,
      },
      timeout: 8000,
    });

    if (resp.data.errcode) {
      logger.error('微信 access_token 获取失败', resp.data);
      throw wechatAPIFailed('微信服务异常', resp.data);
    }

    // 缓存 110 分钟（官方有效期 120 分钟，提前 10 分钟刷新）
    getAccessToken._cached = {
      token: resp.data.access_token,
      expiresAt: Date.now() + 110 * 60 * 1000,
    };

    return resp.data.access_token;
  } catch (err) {
    if (err.code && err.code >= 6000) throw err;
    logger.error('微信 access_token 请求失败', { error: err.message });
    throw wechatAPIFailed('微信服务异常');
  }
}

/**
 * 获取用户手机号（通过 code）
 *
 * 新版本接口（需要 access_token）：
 * POST https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=TOKEN
 *
 * @param {string} code - 微信 getPhoneNumber 返回的 code
 * @returns {Promise<string>} 纯数字手机号
 */
async function getPhoneNumber(code) {
  try {
    const accessToken = await getAccessToken();
    const resp = await axios.post(
      `${WECHAT_BASE}/wxa/business/getuserphonenumber`,
      { code },
      { params: { access_token: accessToken }, timeout: 8000 }
    );

    if (resp.data.errcode && resp.data.errcode !== 0) {
      logger.error('微信手机号获取失败', resp.data);
      throw wechatAPIFailed('获取手机号失败', resp.data);
    }

    const phone = resp.data.phone_info?.purePhoneNumber;
    if (!phone) {
      throw wechatAPIFailed('未获取到手机号');
    }

    return phone;
  } catch (err) {
    if (err.code && err.code >= 6000) throw err;
    logger.error('微信手机号接口请求失败', { error: err.message });
    throw wechatAPIFailed('微信服务异常');
  }
}

module.exports = { getAccessToken, getPhoneNumber };
