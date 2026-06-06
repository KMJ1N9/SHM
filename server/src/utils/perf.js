/**
 * 性能监控工具
 *
 * 提供轻量级性能埋点：
 * - 请求耗时统计（中间件）
 * - 慢查询标记（db.js 中集成）
 * - 内存使用监控
 */

const logger = require('./logger').business;

/**
 * 高精度计时器（process.hrtime）
 * @returns {function(): number} stop() → 返回耗时（毫秒）
 */
function startTimer() {
  const start = process.hrtime.bigint();
  return () => {
    const diff = process.hrtime.bigint() - start;
    return Number(diff) / 1_000_000; // ns → ms
  };
}

/**
 * 请求耗时中间件工厂
 *
 * 在 access-log 之外提供更细粒度的性能数据。
 * 慢请求（> 1000ms）记录 warn 级别日志。
 *
 * @param {number} [slowThreshold=1000] - 慢请求阈值（ms）
 * @returns {function} Express 中间件
 */
function requestTimer(slowThreshold = 1000) {
  return (req, res, next) => {
    const stop = startTimer();

    res.on('finish', () => {
      const duration = stop();
      if (duration > slowThreshold) {
        logger.warn('慢请求', {
          method: req.method,
          url: req.originalUrl,
          duration: `${duration.toFixed(0)}ms`,
          status: res.statusCode,
        });
      }
    });

    next();
  };
}

/**
 * 内存使用快照
 * @returns {{heapUsed: number, heapTotal: number, external: number, rss: number}}
 */
function memoryUsage() {
  const usage = process.memoryUsage();
  return {
    heapUsed: Math.round(usage.heapUsed / 1024 / 1024),
    heapTotal: Math.round(usage.heapTotal / 1024 / 1024),
    external: Math.round(usage.external / 1024 / 1024),
    rss: Math.round(usage.rss / 1024 / 1024),
  };
}

/**
 * 定时内存监控（每 10 分钟输出一次）
 * 仅在 NODE_ENV=development 时启用
 */
function startMemoryMonitor() {
  if (process.env.NODE_ENV === 'development') {
    setInterval(() => {
      const mem = memoryUsage();
      logger.debug('内存使用', mem);
    }, 10 * 60 * 1000);
  }
}

module.exports = { startTimer, requestTimer, memoryUsage, startMemoryMonitor };
