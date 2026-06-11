/**
 * MySQL 连接池初始化
 *
 * 使用 mysql2/promise，提供：
 *   - pool:  连接池实例（供 repository 层获取事务连接）
 *   - query: 便捷查询函数（内部调用 pool.query，参数化查询防 SQL 注入）
 *   - transaction: 事务包装器
 *
 * 慢查询告警（见技术架构文档 §2.8）：
 *   - > 200ms:  warn 级别记录
 *   - > 1000ms: error 级别记录
 *   - > 3000ms: 终止查询并返回 500（mysql2 驱动层 timeout）
 */

const mysql = require('mysql2/promise');
const config = require('../config');
const { error: errorLogger, business: businessLogger } = require('../utils/logger');

const pool = mysql.createPool(config.db);

// 启动时验证数据库连接
pool
  .getConnection()
  .then((conn) => {
    conn.release();
    businessLogger.info('[DB] MySQL 连接成功', {
      host: config.db.host,
      port: config.db.port,
      database: config.db.database,
    });
  })
  .catch((err) => {
    console.error('[DB] MySQL 连接失败:', err.message);
    console.error('[DB] 请检查: 1) MySQL 服务是否启动 2) .env.development 中的 DB_* 配置是否正确');
  });

/**
 * 执行参数化查询（便捷函数）
 *
 * 内部调用 pool.query(sql, params)，客户端参数化转义，
 * 天然防 SQL 注入。禁止传入拼接字符串的 SQL。
 *
 * 内置慢查询监控：
 *   - > 200ms:  warn 级别日志
 *   - > 1000ms: error 级别日志
 *
 * @param {string} sql    - SQL 语句（使用 ? 占位符）
 * @param {Array}  params - 参数数组
 * @returns {Promise<Array>} 查询结果行
 */
async function query(sql, params = []) {
  const start = Date.now();
  try {
    // 使用 pool.query() 而非 pool.execute()：
    // execute() 使用 MySQL 服务端 PREPARE 协议，LIMIT/OFFSET 的
    // 参数化占位符不被支持（Incorrect arguments to mysqld_stmt_execute）。
    // query() 在客户端做参数化转义，功能等价且同样防 SQL 注入。
    const [rows] = await pool.query(sql, params);
    const duration = Date.now() - start;

    if (duration > 1000) {
      errorLogger.error({
        message: '慢查询告警 (>1000ms)',
        sql: sql.substring(0, 500),
        params: JSON.stringify(params).substring(0, 200),
        duration,
      });
    } else if (duration > 200) {
      errorLogger.warn({
        message: '慢查询记录 (>200ms)',
        sql: sql.substring(0, 500),
        params: JSON.stringify(params).substring(0, 200),
        duration,
      });
    }

    return rows;
  } catch (err) {
    const duration = Date.now() - start;
    errorLogger.error({
      message: '数据库查询失败',
      sql: sql.substring(0, 500),
      params: JSON.stringify(params).substring(0, 200),
      duration,
      error: err.message,
    });
    throw err;
  }
}

/**
 * 事务包装器
 *
 * 获取连接 → 开启事务 → 执行业务回调 → 提交/回滚 → 释放连接。
 * 回调函数接收 connection 参数，在事务内执行的查询应使用 conn.query()（与 pool.query() 一致，客户端参数化转义，支持 LIMIT/OFFSET）。
 *
 * @param {Function} callback - async (conn) => result
 * @returns {Promise<*>} 回调函数的返回值
 */
async function transaction(callback) {
  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    const result = await callback(conn);
    await conn.commit();
    return result;
  } catch (err) {
    await conn.rollback();
    throw err;
  } finally {
    conn.release();
  }
}

/**
 * 健康检查：测试数据库连接是否存活
 * @returns {Promise<string>} "connected" | "disconnected"
 */
async function healthCheck() {
  try {
    await pool.execute('SELECT 1');
    return 'connected';
  } catch {
    return 'disconnected';
  }
}

module.exports = { pool, query, transaction, healthCheck };
