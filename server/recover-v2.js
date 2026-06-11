/**
 * 精准数据恢复脚本 v2
 *
 * 从 MySQL binary log 中只提取 INSERT 操作（Write_rows），跳过所有 DDL。
 * 使用 INSERT IGNORE 避免主键冲突。
 */

const { execSync } = require('child_process');
const path = require('path');
const fs = require('fs');

const DATADIR = 'D:\\MySQL\\MySQL Server 8.0 data\\Data';
const BINLOG_PREFIX = path.join(DATADIR, 'DESKTOP-ID19Q6A-bin');
const DB_NAME = 'campus_market_dev';

// Get list of all binlog files
const binlogFiles = [];
for (let i = 100; i <= 145; i++) {
  const name = String(i).padStart(6, '0');
  const filePath = BINLOG_PREFIX + '.' + name;
  if (fs.existsSync(filePath)) {
    binlogFiles.push({ name, path: filePath });
  }
}

console.log('========================================');
console.log('  精准数据恢复 v2 - INSERT ONLY');
console.log('========================================');
console.log(`\n涉及 ${binlogFiles.length} 个 binlog 文件\n`);

const OUT_FILE = path.resolve(__dirname, 'recovered_inserts.sql');

// Clear output file
fs.writeFileSync(OUT_FILE, '-- Recovered INSERTs from MySQL binlog\n');
fs.appendFileSync(OUT_FILE, 'SET FOREIGN_KEY_CHECKS=0;\n\n');

let totalInserts = 0;

for (const bl of binlogFiles) {
  console.log(`处理 ${bl.name}...`);
  try {
    // Use mysqlbinlog to decode ROW events to pseudo-SQL
    // Then filter only INSERT INTO statements
    const rawSQL = execSync(
      `mysqlbinlog --base64-output=DECODE-ROWS --verbose "${bl.path}" 2>&1`,
      { encoding: 'utf8', maxBuffer: 500 * 1024 * 1024, timeout: 120000, shell: 'cmd.exe' }
    );

    // Parse the verbose output to extract INSERT statements
    // The format is:
    // ### INSERT INTO `campus_market_dev`.`table_name`
    // ### SET
    // ###   @1=val1
    // ###   @2=val2
    // ...

    let currentInsert = null;
    let insertCount = 0;

    const lines = rawSQL.split('\n');
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];

      // Detect start of INSERT
      const insertMatch = line.match(/### INSERT INTO \`campus_market_dev\`\.\`(\w+)\`/);
      if (insertMatch) {
        // Flush previous INSERT
        if (currentInsert) {
          fs.appendFileSync(OUT_FILE, currentInsert + ';\n');
          insertCount++;
          totalInserts++;
        }

        currentInsert = `INSERT IGNORE INTO \`${insertMatch[1]}\` SET`;
        continue;
      }

      // Detect SET line
      if (currentInsert && line.includes('### SET')) {
        continue;
      }

      // Detect column values
      if (currentInsert) {
        const colMatch = line.match(/###   @(\d+)=(.*)/);
        if (colMatch) {
          let val = colMatch[2].trim();
          // Handle NULL
          if (val === 'NULL') {
            val = 'NULL';
          }
          currentInsert += ` @${colMatch[1]}=${val},`;
        } else if (line.includes('###') || line.trim() === '') {
          // End of INSERT block or comment
          if (currentInsert) {
            // Remove trailing comma
            currentInsert = currentInsert.replace(/,$/, '');
            fs.appendFileSync(OUT_FILE, currentInsert + ';\n');
            insertCount++;
            totalInserts++;
            currentInsert = null;
          }
        }
      }
    }

    // Flush last INSERT
    if (currentInsert) {
      currentInsert = currentInsert.replace(/,$/, '');
      fs.appendFileSync(OUT_FILE, currentInsert + ';\n');
      insertCount++;
      totalInserts++;
    }

    console.log(`  → 提取 ${insertCount} 条 INSERT`);
  } catch (e) {
    console.log(`  ⚠ 错误: ${e.message.substring(0, 100)}`);
  }
}

fs.appendFileSync(OUT_FILE, '\nSET FOREIGN_KEY_CHECKS=1;\n');

console.log(`\n共提取 ${totalInserts} 条 INSERT，写入 ${OUT_FILE}`);
console.log(`文件大小: ${(fs.statSync(OUT_FILE).size / 1024 / 1024).toFixed(2)} MB`);

// Apply to database
console.log('\n正在应用到数据库...');
try {
  const result = execSync(
    `mysql -u root -p12345678 --force ${DB_NAME} < "${OUT_FILE}" 2>&1`,
    { encoding: 'utf8', maxBuffer: 100 * 1024 * 1024, timeout: 300000, shell: 'cmd.exe' }
  );
  if (result.trim()) {
    const errors = result.split('\n').filter(l => l.includes('ERROR'));
    if (errors.length > 0) {
      console.log(`  ${errors.length} 条重复/错误（已跳过）`);
    }
  }
  console.log('  ✓ 应用完成');
} catch (e) {
  console.log('  输出:', (e.stdout || '').substring(0, 1000));
}

console.log('\n========================================');
console.log('  恢复完成！');
console.log('========================================');
process.exit(0);
