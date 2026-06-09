/**
 * 校园二手交易小程序 — 定时任务调度器
 *
 * PM2 fork 模式单进程运行，避免 cluster 模式下重复执行。
 * 启动方式：node scheduler.js
 * PM2 配置：ecosystem.config.js 中配置为独立进程（fork mode, instances: 1）
 *
 * 定时任务清单：
 *   1. 每 5 分钟 — 超时未面交的订单自动取消（下单后 24h 未标记"已面交"）
 *   2. 每 5 分钟 — 已面交但超时未确认的订单自动确认（面交后 72h 未确认收货）
 *   3. 每日凌晨 3:00 — 数据库备份
 *   4. 工作日凌晨 4:00 — 归档 6 个月以上的 admin_logs 和 reviews
 */

const cron = require('node-cron');
const config = require('./src/config');
const logger = require('./src/utils/logger');
const db = require('./src/models/db');
const imProvider = require('./src/services/im/provider');
const userRepo = require('./src/repository/user');

// ============================================================
// 工具函数
// ============================================================

/**
 * 安全执行定时任务，捕获异常防止进程崩溃
 * @param {string} name - 任务名称（用于日志）
 * @param {Function} fn - 异步任务函数
 */
async function safeRun(name, fn) {
  const start = Date.now();
  try {
    logger.info(`[CRON] ${name} — 开始执行`);
    await fn();
    const duration = Date.now() - start;
    logger.info(`[CRON] ${name} — 完成 (${duration}ms)`);
  } catch (err) {
    const duration = Date.now() - start;
    logger.error(`[CRON] ${name} — 失败 (${duration}ms): ${err.message}`, {
      stack: err.stack,
    });
  }
}

// ============================================================
// 1. 超时自动取消（每 5 分钟）
//    — 下单后 24h 未标记"已面交"的订单 → 自动取消
// ============================================================
async function cancelTimeoutOrders() {
  const sql = `
    UPDATE orders
    SET status = 'cancelled', cancelled_by = 'system', updated_at = NOW()
    WHERE status = 'pending'
      AND created_at < DATE_SUB(NOW(), INTERVAL 24 HOUR)
  `;
  const [result] = await db.pool.execute(sql);
  if (result.affectedRows > 0) {
    logger.info(`[CRON] 自动取消 ${result.affectedRows} 笔超时订单`);

    // 退还商品状态为 active（允许重新被购买）
    const restoreSql = `
      UPDATE products p
      JOIN orders o ON p.id = o.product_id
      SET p.status = 'active', p.updated_at = NOW()
      WHERE o.status = 'cancelled'
        AND o.cancelled_by = 'system'
        AND o.updated_at >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)
    `;
    await db.pool.execute(restoreSql);

    // IM 通知买卖双方：订单超时自动取消
    const [cancelledOrders] = await db.pool.execute(
      `SELECT o.id, o.buyer_id, o.seller_id, o.product_snapshot
       FROM orders o
       WHERE o.status = 'cancelled'
         AND o.cancelled_by = 'system'
         AND o.updated_at >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)`
    );
    for (const order of cancelledOrders) {
      const title = (order.product_snapshot && order.product_snapshot.title)
        ? order.product_snapshot.title
        : '商品';
      const msg = {
        title: '订单超时自动取消',
        content: `「${title}」的订单超过 24 小时未面交，系统已自动取消`,
        extra: { type: 'order', order_id: order.id, status: 'cancelled' },
      };
      imProvider.sendSystemMessage(order.buyer_id, msg).catch(() => {});
      imProvider.sendSystemMessage(order.seller_id, msg).catch(() => {});
    }
  }
}

// ============================================================
// 2. 超时自动确认（每 5 分钟）
//    — 面交后 72h 未确认收货的订单 → 自动确认
// ============================================================
async function confirmTimeoutOrders() {
  const sql = `
    UPDATE orders
    SET status = 'completed', confirmed_at = NOW(), updated_at = NOW()
    WHERE status = 'met'
      AND met_at < DATE_SUB(NOW(), INTERVAL 72 HOUR)
  `;
  const [result] = await db.pool.execute(sql);
  if (result.affectedRows > 0) {
    logger.info(`[CRON] 自动确认 ${result.affectedRows} 笔超时订单`);

    // 查询本次自动确认的订单详情
    const [completedOrders] = await db.pool.execute(
      `SELECT o.id, o.buyer_id, o.seller_id, o.product_snapshot
       FROM orders o
       WHERE o.status = 'completed'
         AND o.confirmed_at >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)`
    );
    for (const order of completedOrders) {
      // 卖家信誉分 +2
      userRepo.updateCreditScore(
        order.seller_id,
        config.credit.rewardTransaction,
        config.credit.max
      ).catch(() => {});

      // IM 通知双方互评
      const title = (order.product_snapshot && order.product_snapshot.title)
        ? order.product_snapshot.title
        : '商品';
      const msg = {
        title: '订单超时自动确认',
        content: `「${title}」的订单已超过 72 小时未确认收货，系统已自动完成`,
        extra: { type: 'review_remind', order_id: order.id },
      };
      imProvider.sendSystemMessage(order.buyer_id, msg).catch(() => {});
      imProvider.sendSystemMessage(order.seller_id, msg).catch(() => {});
    }
  }
}

// ============================================================
// 3. 数据库备份（每日凌晨 3:00）
//    — 执行 mysqldump 导出到 ./backups/ 目录
// ============================================================
async function backupDatabase() {
  const { execSync } = require('child_process');
  const path = require('path');
  const fs = require('fs');

  const backupDir = path.resolve(__dirname, 'backups');
  if (!fs.existsSync(backupDir)) {
    fs.mkdirSync(backupDir, { recursive: true });
  }

  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').substring(0, 19);
  const filename = `${config.db.database}_${timestamp}.sql.gz`;
  const filepath = path.join(backupDir, filename);

  try {
    execSync(
      `mysqldump -h ${config.db.host} -P ${config.db.port} -u ${config.db.user} ` +
        `-p${config.db.password} --single-transaction --routines --triggers ` +
        `${config.db.database} | gzip > "${filepath}"`,
      { timeout: 300000, stdio: 'pipe' }
    );
    logger.info(`[CRON] 数据库备份完成: ${filename}`);

    // 清理 30 天前的备份
    const files = fs.readdirSync(backupDir)
      .filter(f => f.endsWith('.sql.gz'))
      .sort();
    while (files.length > 30) {
      const oldFile = files.shift();
      fs.unlinkSync(path.join(backupDir, oldFile));
      logger.info(`[CRON] 清理旧备份: ${oldFile}`);
    }
  } catch (err) {
    logger.error(`[CRON] 数据库备份失败: ${err.message}`);
  }
}

// ============================================================
// 4. 归档管理日志和评价（工作日凌晨 4:00）
//    — 归档 6 个月以上的 admin_logs → admin_logs_archive
//    — 归档 6 个月以上的 reviews（已完成订单） → reviews_archive
// ============================================================
async function archiveOldData() {
  // 归档 admin_logs
  const [adminResult] = await db.pool.execute(`
    INSERT INTO admin_logs_archive (id, admin_id, action, target_type, target_id, reason, created_at, archived_at)
    SELECT id, admin_id, action, target_type, target_id, reason, created_at, NOW()
    FROM admin_logs
    WHERE created_at < DATE_SUB(NOW(), INTERVAL 6 MONTH)
  `);
  if (adminResult.affectedRows > 0) {
    await db.pool.execute(`
      DELETE FROM admin_logs
      WHERE created_at < DATE_SUB(NOW(), INTERVAL 6 MONTH)
    `);
    logger.info(`[CRON] 归档 ${adminResult.affectedRows} 条管理日志`);
  }

  // 归档已完成订单的评价
  const [reviewResult] = await db.pool.execute(`
    INSERT INTO reviews_archive (id, order_id, reviewer_id, reviewee_id,
      communication_score, punctuality_score, accuracy_score, comment, created_at, archived_at)
    SELECT r.id, r.order_id, r.reviewer_id, r.reviewee_id,
      r.communication_score, r.punctuality_score, r.accuracy_score, r.comment, r.created_at, NOW()
    FROM reviews r
    JOIN orders o ON r.order_id = o.id
    WHERE o.status = 'completed'
      AND o.confirmed_at < DATE_SUB(NOW(), INTERVAL 6 MONTH)
  `);
  if (reviewResult.affectedRows > 0) {
    // 归档后不从原表删除（评价是永久的信誉记录）
    logger.info(`[CRON] 归档 ${reviewResult.affectedRows} 条评价`);
  }
}

// ============================================================
// 启动定时任务
// ============================================================

function start() {
  if (!config.enableCron) {
    console.log('[CRON] 定时任务已禁用（ENABLE_CRON=false），退出。');
    logger.info('[CRON] 定时任务已禁用，调度器退出。');
    return;
  }

  console.log('⏰ 定时任务调度器启动...');
  logger.info('[CRON] 调度器启动');

  // 每 5 分钟：超时自动取消
  cron.schedule('*/5 * * * *', () => {
    safeRun('超时自动取消', cancelTimeoutOrders);
  });

  // 每 5 分钟：超时自动确认
  cron.schedule('*/5 * * * *', () => {
    safeRun('超时自动确认', confirmTimeoutOrders);
  });

  // 每日凌晨 3:00：数据库备份
  cron.schedule('0 3 * * *', () => {
    safeRun('数据库备份', backupDatabase);
  });

  // 工作日凌晨 4:00：数据归档
  cron.schedule('0 4 * * 1-5', () => {
    safeRun('数据归档', archiveOldData);
  });

  console.log('   ✓ 超时自动取消 — 每 5 分钟');
  console.log('   ✓ 超时自动确认 — 每 5 分钟');
  console.log('   ✓ 数据库备份     — 每日 03:00');
  console.log('   ✓ 数据归档       — 工作日 04:00');
  console.log('');
}

// 仅在直接运行时启动
if (require.main === module) {
  start();
}

module.exports = { start, cancelTimeoutOrders, confirmTimeoutOrders, backupDatabase, archiveOldData };
