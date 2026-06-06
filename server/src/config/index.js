/**
 * 环境配置集中管理
 *
 * 从 .env 文件和环境变量中加载配置，提供类型转换和默认值。
 * 所有环境变量读取必须通过本模块，禁止在其他文件中直接访问 process.env。
 *
 * 使用方式：
 *   const config = require('../config');
 *   const pool = mysql.createPool(config.db);
 */

const path = require('path');
const dotenv = require('dotenv');

// 按 NODE_ENV 加载对应的 .env 文件
const envFile = `.env.${process.env.NODE_ENV || 'development'}`;
const envPath = path.resolve(__dirname, '../..', envFile);

const result = dotenv.config({ path: envPath });
if (result.error) {
  // .env.development 不存在时回退到 .env.example 模板并告警
  const fallback = path.resolve(__dirname, '../..', '.env.example');
  dotenv.config({ path: fallback });
  console.warn(
    `[WARN] 未找到 ${envFile}，使用 .env.example 模板。请复制 .env.example → ${envFile} 并填入真实密钥。`
  );
}

/**
 * 获取必填环境变量，缺失时抛出明确错误
 * @param {string} key - 环境变量名
 * @returns {string}
 */
function required(key) {
  const val = process.env[key];
  if (val === undefined || val === null || val === '') {
    throw new Error(`[CONFIG] 缺少必填环境变量: ${key}。请检查 ${envFile}。`);
  }
  return val;
}

/**
 * 获取可选环境变量，缺失时返回默认值
 * @param {string} key
 * @param {*} defaultValue
 * @returns {string}
 */
function optional(key, defaultValue) {
  const val = process.env[key];
  if (val === undefined || val === null || val === '') {
    return defaultValue;
  }
  return val;
}

const config = {
  // ---- 服务器 ----
  port: parseInt(optional('PORT', '3000'), 10),
  nodeEnv: optional('NODE_ENV', 'development'),

  // ---- 微信小程序 ----
  wx: {
    appId: required('WX_APPID'),
    appSecret: required('WX_APPSECRET'),
  },

  // ---- JWT ----
  jwt: {
    accessSecret: required('JWT_ACCESS_SECRET'),
    refreshSecret: required('JWT_REFRESH_SECRET'),
    accessExpires: optional('JWT_ACCESS_EXPIRES', '7d'),
    refreshExpires: optional('JWT_REFRESH_EXPIRES', '30d'),
  },

  // ---- 腾讯云 IM ----
  im: {
    sdkAppId: parseInt(required('IM_SDK_APP_ID'), 10),
    secretKey: required('IM_SECRET_KEY'),
    adminAccount: optional('IM_ADMIN_ACCOUNT', 'administrator'),
  },

  // ---- 腾讯云 COS ----
  cos: {
    bucket: required('COS_BUCKET'),
    region: required('COS_REGION'),
    secretId: required('COS_SECRET_ID'),
    secretKey: required('COS_SECRET_KEY'),
    cdnBaseUrl: optional('COS_CDN_BASE_URL', ''),
    uploadMaxSize: parseInt(optional('COS_UPLOAD_MAX_SIZE', '5242880'), 10),
    uploadAllowedTypes: optional(
      'COS_UPLOAD_ALLOWED_TYPES',
      'image/jpeg,image/png,image/webp'
    ).split(','),
  },

  // ---- MySQL ----
  db: {
    host: optional('DB_HOST', '127.0.0.1'),
    port: parseInt(optional('DB_PORT', '3306'), 10),
    user: optional('DB_USER', 'root'),
    password: optional('DB_PASSWORD', ''),
    database: optional('DB_NAME', 'campus_market_dev'),
    charset: 'utf8mb4',
    waitForConnections: true,
    connectionLimit: parseInt(optional('DB_CONNECTION_LIMIT', '10'), 10),
    queueLimit: 0,
    enableKeepAlive: true,
    keepAliveInitialDelay: 10000,
  },

  // ---- 日志 ----
  log: {
    level: optional('LOG_LEVEL', 'debug'),
    dir: optional('LOG_DIR', './logs'),
  },

  // ---- 限流 ----
  rateLimit: {
    global: parseInt(optional('RATE_LIMIT_GLOBAL', '60'), 10),
    sensitive: parseInt(optional('RATE_LIMIT_SENSITIVE', '10'), 10),
  },

  // ---- CORS ----
  corsOrigin: optional('CORS_ORIGIN', '*'),

  // ---- 定时任务 ----
  enableCron: optional('ENABLE_CRON', 'false') === 'true',

  // ---- 信誉分 ----
  credit: {
    initial: parseInt(optional('CREDIT_INITIAL', '100'), 10),
    max: parseInt(optional('CREDIT_MAX', '200'), 10),
    deductReport: parseInt(optional('CREDIT_DEDUCT_REPORT', '30'), 10),
    rewardTransaction: parseInt(optional('CREDIT_REWARD_TRANSACTION', '2'), 10),
    rewardPositiveReview: parseInt(
      optional('CREDIT_REWARD_POSITIVE_REVIEW', '1'),
      10
    ),
    deductNegativeReview: parseInt(
      optional('CREDIT_DEDUCT_NEGATIVE_REVIEW', '5'),
      10
    ),
    publishThreshold: parseInt(optional('CREDIT_PUBLISH_THRESHOLD', '60'), 10),
    tradeThreshold: parseInt(optional('CREDIT_TRADE_THRESHOLD', '30'), 10),
  },

  // ---- 举报 SLA ----
  report: {
    responseSlaHours: parseInt(
      optional('REPORT_RESPONSE_SLA_HOURS', '3'),
      10
    ),
    resolveSlaDays: parseInt(optional('REPORT_RESOLVE_SLA_DAYS', '3'), 10),
  },
};

module.exports = config;
