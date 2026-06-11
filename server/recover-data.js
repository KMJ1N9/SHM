/**
 * 数据恢复脚本
 *
 * 从 MySQL binary log 中恢复被 db:migrate:reset 误删的数据。
 *
 * 原理：
 *   第一个 DROP TABLE 发生在 binlog.000143 的 end_log_pos=4856（2026-06-09 21:43:33）
 *   将此之前的所有 binlog 事件（INSERT/UPDATE/DELETE）重放到数据库。
 *
 * 用法：node recover-data.js
 */

const { execSync, exec } = require('child_process');
const path = require('path');
const fs = require('fs');

const DATADIR = 'D:\\MySQL\\MySQL Server 8.0 data\\Data';
const BINLOG_PREFIX = path.join(DATADIR, 'DESKTOP-ID19Q6A-bin');
const DB_NAME = 'campus_market_dev';

// 第一个 DROP TABLE 的精确位置
const DROP_BINLOG = '000143';
const DROP_POSITION = 4856;

// 需要恢复的 binlog 范围（从最早到 000143 位置 4856）
const BINLOGS_TO_REPLAY = [];
for (let i = 100; i <= 142; i++) {
  const name = String(i).padStart(6, '0');
  const filePath = BINLOG_PREFIX + '.' + name;
  if (fs.existsSync(filePath)) {
    BINLOGS_TO_REPLAY.push({ name, path: filePath, stopPos: null });
  }
}
// 最后一个 binlog 需要在 DROP TABLE 之前停止
BINLOGS_TO_REPLAY.push({
  name: DROP_BINLOG,
  path: BINLOG_PREFIX + '.' + DROP_BINLOG,
  stopPos: DROP_POSITION,
});

console.log('========================================');
console.log('  数据恢复脚本');
console.log('========================================');
console.log(`\n将重放 ${BINLOGS_TO_REPLAY.length} 个 binlog 文件`);
console.log(`从 ${BINLOGS_TO_REPLAY[0].name} 到 ${DROP_BINLOG} (pos ${DROP_POSITION})`);
console.log(`数据库: ${DB_NAME}\n`);

// Step 1: 备份当前数据库
console.log('Step 1/3: 备份当前数据库...');
try {
  execSync(
    `mysqldump -u root -p12345678 --single-transaction ${DB_NAME} > recover_backup_${Date.now()}.sql`,
    { encoding: 'utf8', timeout: 30000, shell: 'cmd.exe' }
  );
  console.log('  ✓ 备份完成');
} catch (e) {
  console.log('  ⚠ 备份失败（继续恢复）:', e.message.substring(0, 100));
}

// Step 2: 构建 mysqlbinlog 命令
console.log('\nStep 2/3: 从 binlog 提取数据...');

const binlogArgs = BINLOGS_TO_REPLAY.map(bl => {
  let arg = `"${bl.path}"`;
  if (bl.stopPos) {
    arg += ` --stop-position=${bl.stopPos}`;
  }
  return arg;
}).join(' ');

const cmd = `mysqlbinlog --database=${DB_NAME} --force-read ${binlogArgs} 2>&1`;

console.log('  正在重放 binlog 事件到 MySQL...');
console.log(`  涉及 ${BINLOGS_TO_REPLAY.length} 个文件，可能需要几分钟...\n`);

// Step 3: 重放
try {
  // 先清空当前 seed 数据（保留表结构和 auto_increment）
  execSync(
    `mysql -u root -p12345678 ${DB_NAME} -e "SET FOREIGN_KEY_CHECKS=0; TRUNCATE TABLE notifications; TRUNCATE TABLE admin_logs; TRUNCATE TABLE report_evidence; TRUNCATE TABLE product_images; TRUNCATE TABLE reviews; TRUNCATE TABLE reports; TRUNCATE TABLE orders; TRUNCATE TABLE products; TRUNCATE TABLE users; SET FOREIGN_KEY_CHECKS=1;"`,
    { encoding: 'utf8', timeout: 10000, shell: 'cmd.exe' }
  );
  console.log('  ✓ 已清空当前 seed 数据');

  // 重放 binlog 事件
  const result = execSync(
    `mysqlbinlog --database=${DB_NAME} --force-read ${binlogArgs} | mysql -u root -p12345678 --force ${DB_NAME} 2>&1`,
    { encoding: 'utf8', maxBuffer: 500 * 1024 * 1024, timeout: 600000, shell: 'cmd.exe' }
  );

  if (result.trim()) {
    console.log('  MySQL 输出:');
    console.log(result.substring(0, 2000));
  }
} catch (e) {
  console.log('  重放过程中的输出:');
  console.log((e.stdout || '').substring(0, 1000));
  console.log((e.stderr || e.message).substring(0, 2000));
}

console.log('\nStep 3/3: 验证恢复结果...');
try {
  const { execSync: es } = require('child_process');
  const verify = es(
    `mysql -u root -p12345678 ${DB_NAME} -e "SELECT COUNT(*) as user_count FROM users; SELECT COUNT(*) as product_count FROM products; SELECT COUNT(*) as report_count FROM reports; SELECT COUNT(*) as order_count FROM orders; SELECT COUNT(*) as review_count FROM reviews;"`,
    { encoding: 'utf8', timeout: 10000, shell: 'cmd.exe' }
  );
  console.log(verify);
} catch (e) {
  console.log('验证失败:', e.message.substring(0, 200));
}

console.log('\n========================================');
console.log('  恢复完成！请检查数据。');
console.log('  如有问题，备份文件为 recover_backup_*.sql');
console.log('========================================');
process.exit(0);
