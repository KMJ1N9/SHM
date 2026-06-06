/**
 * 请求日志中间件
 *
 * 记录每个 HTTP 请求的基本信息：方法、路径、状态码、响应时间。
 * 在 res.on('finish') 事件中记录——此时响应已发送完毕，状态码确定。
 *
 * 开发环境：控制台 compact 输出
 * 生产环境：access logger → 按天切割的日志文件
 */

const logger = require('../utils/logger');

/**
 * 请求访问日志中间件
 */
function accessLog(req, res, next) {
  const start = Date.now();

  res.on('finish', () => {
    const duration = Date.now() - start;
    const logData = {
      method: req.method,
      url: req.originalUrl,
      status: res.statusCode,
      duration: `${duration}ms`,
      ip: req.ip || req.headers['x-forwarded-for'] || '-',
      userAgent: (req.headers['user-agent'] || '-').substring(0, 100),
    };

    if (res.statusCode >= 500) {
      logger.access.error(logData);
    } else if (res.statusCode >= 400) {
      logger.access.warn(logData);
    } else {
      logger.access.info(logData);
    }
  });

  next();
}

module.exports = accessLog;
