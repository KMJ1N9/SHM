/**
 * 数据库迁移运行器
 *
 * 命令行入口：node src/models/migrate.js [command]
 * 命令：
 *   migrate       — 执行未运行的迁移
 *   rollback      — 回滚最近一批迁移
 *   status        — 查看迁移状态
 *   reset         — 回滚所有 → 重新执行（开发用）
 */

const path = require('path');
const fs = require('fs');
const db = require('./db');

const MIGRATIONS_DIR = path.resolve(__dirname, '../../migrations');

/**
 * 确保 migrations 表存在
 */
async function ensureMigrationsTable() {
  await db.query(`
    CREATE TABLE IF NOT EXISTS migrations (
      id          INT PRIMARY KEY AUTO_INCREMENT,
      name        VARCHAR(255) NOT NULL UNIQUE,
      batch       INT NOT NULL,
      executed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);
}

/**
 * 获取已执行的迁移
 * @returns {Promise<Array<string>>}
 */
async function getExecutedMigrations() {
  const rows = await db.query('SELECT name FROM migrations ORDER BY id ASC');
  return rows.map(r => r.name);
}

/**
 * 获取迁移文件列表（按文件名排序）
 * @returns {string[]}
 */
function getMigrationFiles() {
  if (!fs.existsSync(MIGRATIONS_DIR)) {
    fs.mkdirSync(MIGRATIONS_DIR, { recursive: true });
    return [];
  }
  return fs
    .readdirSync(MIGRATIONS_DIR)
    .filter(f => f.endsWith('.js'))
    .sort();
}

/**
 * 执行向上迁移
 */
async function migrate() {
  await ensureMigrationsTable();

  const executed = await getExecutedMigrations();
  const files = getMigrationFiles();
  const pending = files.filter(f => !executed.includes(f));

  if (pending.length === 0) {
    console.log('没有待执行的迁移。');
    return;
  }

  // 获取当前批次号
  const [batchRow] = await db.query(
    'SELECT COALESCE(MAX(batch), 0) + 1 AS next_batch FROM migrations'
  );
  const batch = batchRow.next_batch;

  console.log(`批次 ${batch}: 执行 ${pending.length} 个迁移...`);

  for (const file of pending) {
    const migration = require(path.join(MIGRATIONS_DIR, file));

    if (typeof migration.up !== 'function') {
      throw new Error(`迁移文件 ${file} 缺少 up() 函数`);
    }

    console.log(`  ↑ ${file}...`);
    await migration.up(db);
    await db.query('INSERT INTO migrations (name, batch) VALUES (?, ?)', [file, batch]);
    console.log(`  ✓ ${file}`);
  }

  console.log(`迁移完成（批次 ${batch}，${pending.length} 个文件）。`);
}

/**
 * 回滚最近一批迁移
 */
async function rollback() {
  await ensureMigrationsTable();

  const [batchRow] = await db.query(
    'SELECT MAX(batch) AS last_batch FROM migrations'
  );
  if (!batchRow.last_batch) {
    console.log('没有可回滚的迁移。');
    return;
  }

  const rows = await db.query(
    'SELECT name FROM migrations WHERE batch = ? ORDER BY id DESC',
    [batchRow.last_batch]
  );

  console.log(`回滚批次 ${batchRow.last_batch}: ${rows.length} 个迁移...`);

  for (const { name } of rows) {
    const migration = require(path.join(MIGRATIONS_DIR, name));

    if (typeof migration.down !== 'function') {
      console.warn(`  ⚠ ${name} 缺少 down() 函数，跳过。`);
      continue;
    }

    console.log(`  ↓ ${name}...`);
    await migration.down(db);
    await db.query('DELETE FROM migrations WHERE name = ?', [name]);
    console.log(`  ✓ ${name}`);
  }

  console.log('回滚完成。');
}

/**
 * 查看迁移状态
 */
async function status() {
  await ensureMigrationsTable();

  const executed = await getExecutedMigrations();
  const files = getMigrationFiles();

  console.log('迁移状态：\n');
  for (const file of files) {
    const done = executed.includes(file);
    console.log(`  ${done ? '✓' : '○'} ${file}`);
  }

  const pending = files.filter(f => !executed.includes(f));
  console.log(`\n已执行: ${executed.length}, 待执行: ${pending.length}`);
}

/**
 * 重置（回滚全部 → 重新执行）
 */
async function reset() {
  console.log('重置数据库...');
  while (true) {
    const [batchRow] = await db.query('SELECT MAX(batch) AS last_batch FROM migrations');
    if (!batchRow.last_batch) break;
    await rollback();
  }
  console.log('所有迁移已回滚，重新执行...');
  await migrate();
}

// CLI 入口
const command = process.argv[2] || 'migrate';

(async () => {
  try {
    switch (command) {
      case 'migrate':   await migrate();   break;
      case 'rollback':  await rollback();  break;
      case 'status':    await status();    break;
      case 'reset':     await reset();     break;
      default:
        console.error(`未知命令: ${command}（可用: migrate | rollback | status | reset）`);
        process.exit(1);
    }
    process.exit(0);
  } catch (err) {
    console.error('迁移失败:', err.message);
    console.error(err);
    process.exit(1);
  }
})();
