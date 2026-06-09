/**
 * 测试环境初始化
 *
 * 每个测试套件运行前：
 *   1. 切换为测试数据库（campus_market_test）
 *   2. 执行迁移（创建表结构）
 *   3. 提供测试辅助工具（factory 函数、token 生成等）
 *
 * 使用方式（vitest.config.js 中配置 setupFiles）：
 *   import { setupTestDb, teardownTestDb, createTestUser, authHeader } from './setup.js';
 */

const path = require('path');
const dotenv = require('dotenv');

// 强制加载测试环境变量
dotenv.config({ path: path.resolve(__dirname, '../.env.test') });

// 覆盖 DB_NAME 为测试数据库
process.env.DB_NAME = process.env.DB_NAME || 'campus_market_test';

const db = require('../src/models/db');
const jwt = require('jsonwebtoken');
const config = require('../src/config');

/**
 * 初始化测试数据库（迁移表结构）
 */
async function setupTestDb() {
  // 创建测试数据库（如果不存在）
  try {
    const mysql = require('mysql2/promise');
    const conn = await mysql.createConnection({
      host: config.db.host,
      port: config.db.port,
      user: config.db.user,
      password: config.db.password,
    });
    await conn.query(
      `CREATE DATABASE IF NOT EXISTS \`${config.db.database}\` CHARACTER SET utf8mb4`
    );
    await conn.end();
  } catch (err) {
    console.warn('[TEST SETUP] 创建测试数据库失败（可能已存在）:', err.message);
  }

  // 删除所有表（倒序防止外键约束），再执行迁移
  await db.query('SET FOREIGN_KEY_CHECKS = 0');
  const tables = [
    'user_events', 'notifications', 'report_evidence', 'product_images',
    'admin_logs_archive', 'reviews_archive', 'admin_logs',
    'reports', 'reviews', 'orders', 'products', 'users',
  ];
  for (const table of tables) {
    await db.query(`DROP TABLE IF EXISTS \`${table}\``);
  }
  await db.query('SET FOREIGN_KEY_CHECKS = 1');

  // 执行迁移
  const migration = require('../migrations/001_create_tables');
  await migration.up(db);
}

/**
 * 清理测试数据库
 */
async function teardownTestDb() {
  const tables = [
    'user_events', 'notifications', 'report_evidence', 'product_images',
    'admin_logs_archive', 'reviews_archive', 'admin_logs',
    'reports', 'reviews', 'orders', 'products', 'users',
  ];
  for (const table of tables) {
    try {
      await db.query(`DELETE FROM \`${table}\``);
    } catch {
      // 表可能不存在，忽略
    }
  }
}

/**
 * 创建测试用户
 * @param {Object} overrides
 * @returns {Promise<Object>}
 */
async function createTestUser(overrides = {}) {
  const shortId = Math.random().toString(36).substring(2, 8);
  // db.query() 包装了 pool.query：INSERT 返回 ResultSetHeader（不可数组解构）
  const insertResult = await db.query(
    `INSERT INTO users (phone, nickname, role, status, credit_score)
     VALUES (?, ?, ?, ?, ?)`,
    [
      overrides.phone || `138${shortId.padStart(8, '0')}`,
      overrides.nickname || `测试用户_${shortId}`,
      overrides.role || 'user',
      overrides.status || 'active',
      overrides.credit_score || 100,
    ]
  );
  // SELECT 返回 rows[]；取第一行即为用户对象
  const [user] = await db.query(
    'SELECT id, phone, nickname, avatar, class_name, dorm_building, role, status, token_version, credit_score FROM users WHERE id = ?',
    [insertResult.insertId]
  );
  return user;
}

/**
 * 生成 JWT Authorization Header
 * @param {Object} user - 用户对象（需含 id, role, token_version）
 * @returns {{Authorization: string}}
 */
function authHeader(user) {
  const token = jwt.sign(
    { sub: user.id, role: user.role, tv: user.token_version },
    config.jwt.accessSecret,
    { expiresIn: '7d' }
  );
  return { Authorization: `Bearer ${token}` };
}

module.exports = {
  setupTestDb,
  teardownTestDb,
  createTestUser,
  authHeader,
  db,
};
