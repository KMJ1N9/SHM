/**
 * 令牌桶限流中间件
 *
 * 基于内存的令牌桶算法实现：
 *   - 全局限流：默认 60 req/min，所有 /api/* 请求
 *   - 敏感接口限流：默认 10 req/min，登录/发布/举报等接口
 *
 * MVP 阶段使用内存存储（单进程），后续可平滑替换为 Redis 方案。
 * 开发环境可通过配置将限流值设为 999 以关闭限流。
 *
 * 使用方式：
 *   const { globalLimiter, sensitiveLimiter } = require('../middleware/rate-limiter');
 *   app.use('/api', globalLimiter);                          // 全局
 *   router.post('/api/auth/login', sensitiveLimiter, ...);   // 敏感接口
 */

const config = require('../config');
const errors = require('../utils/errors');

/**
 * 令牌桶实现
 */
class TokenBucket {
  /**
   * @param {number} capacity - 桶容量（最大请求数/分钟）
   * @param {number} refillRate - 每秒补充令牌数
   */
  constructor(capacity, refillRate) {
    this.capacity = capacity;
    this.tokens = capacity;
    this.refillRate = refillRate; // tokens per second
    this.lastRefill = Date.now();
  }

  /** 尝试消费一个令牌 */
  consume() {
    this.refill();
    if (this.tokens >= 1) {
      this.tokens -= 1;
      return true;
    }
    return false;
  }

  /** 按时间差补充令牌 */
  refill() {
    const now = Date.now();
    const elapsed = (now - this.lastRefill) / 1000;
    this.tokens = Math.min(
      this.capacity,
      this.tokens + elapsed * this.refillRate
    );
    this.lastRefill = now;
  }

  /** 获取建议的重试等待时间（秒） */
  getRetryAfter() {
    this.refill();
    if (this.tokens >= 1) return 0;
    return Math.ceil((1 - this.tokens) / this.refillRate);
  }
}

// 全局令牌桶：容量 = RATE_LIMIT_GLOBAL，每秒补充 capacity/60
const globalBucket = new TokenBucket(
  config.rateLimit.global,
  config.rateLimit.global / 60
);

// 敏感接口令牌桶
const sensitiveBucket = new TokenBucket(
  config.rateLimit.sensitive,
  config.rateLimit.sensitive / 60
);

/**
 * 全局限流中间件（所有 /api/* 请求）
 */
function globalLimiter(req, res, next) {
  if (!globalBucket.consume()) {
    const retryAfter = globalBucket.getRetryAfter();
    res.set('Retry-After', String(retryAfter));
    throw errors.rateLimited();
  }
  next();
}

/**
 * 敏感接口限流中间件（登录/发布/举报）
 */
function sensitiveLimiter(req, res, next) {
  if (!sensitiveBucket.consume()) {
    const retryAfter = sensitiveBucket.getRetryAfter();
    res.set('Retry-After', String(retryAfter));
    throw errors.rateLimitedSensitive(retryAfter);
  }
  next();
}

module.exports = { globalLimiter, sensitiveLimiter };
