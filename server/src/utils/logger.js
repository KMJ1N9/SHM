/**
 * winston 日志实例
 *
 * 三级日志：
 *   - access:  请求日志（HTTP 方法、路径、状态码、耗时）
 *   - error:   错误日志（堆栈、traceId、请求上下文）
 *   - business: 业务日志（关键操作：登录、下单、举报处理等）
 *
 * 开发环境 (NODE_ENV=development)：仅输出到控制台
 * 生产环境 (NODE_ENV=production)： 按天切割到文件
 */

const path = require('path');
const winston = require('winston');
const DailyRotateFile = require('winston-daily-rotate-file');
const config = require('../config');

const logDir = path.resolve(__dirname, '../..', config.log.dir);
const nodeEnv = config.nodeEnv;

// 通用日志格式
const format = winston.format.combine(
  winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss.SSS' }),
  winston.format.errors({ stack: true }),
  nodeEnv === 'development'
    ? winston.format.combine(
        winston.format.colorize(),
        winston.format.printf(
          ({ timestamp, level, message, stack, ...meta }) => {
            const metaStr = Object.keys(meta).length
              ? ` ${JSON.stringify(meta)}`
              : '';
            if (stack) {
              return `${timestamp} ${level}: ${message}\n${stack}${metaStr}`;
            }
            return `${timestamp} ${level}: ${message}${metaStr}`;
          }
        )
      )
    : winston.format.json()
);

// 生产环境：按天切割的文件 transport
const fileTransport = (filename) =>
  new DailyRotateFile({
    filename: path.join(logDir, `${filename}-%DATE%.log`),
    datePattern: 'YYYY-MM-DD',
    maxSize: '20m',
    maxFiles: '30d',
    format: winston.format.json(),
  });

// 构建 transport 列表
const transports = [];
if (nodeEnv === 'development') {
  transports.push(new winston.transports.Console({ format }));
} else {
  transports.push(
    new winston.transports.Console({
      format: winston.format.combine(
        winston.format.colorize(),
        winston.format.printf(
          ({ timestamp, level, message }) => `${timestamp} ${level}: ${message}`
        )
      ),
      level: 'warn', // 生产环境控制台只输出 warn 以上
    })
  );
}

// 创建各 logger 实例
const accessLogger = winston.createLogger({
  level: 'info',
  transports:
    nodeEnv === 'development'
      ? [new winston.transports.Console({ format })]
      : [fileTransport('access'), ...transports],
});

const errorLogger = winston.createLogger({
  level: 'error',
  transports:
    nodeEnv === 'development'
      ? [new winston.transports.Console({ format })]
      : [fileTransport('error'), ...transports],
});

const businessLogger = winston.createLogger({
  level: config.log.level,
  transports:
    nodeEnv === 'development'
      ? [new winston.transports.Console({ format })]
      : [fileTransport('business'), ...transports],
});

module.exports = {
  /** 请求访问日志 */
  access: accessLogger,
  /** 错误日志（含堆栈） */
  error: errorLogger,
  /** 业务操作日志 */
  business: businessLogger,
};
