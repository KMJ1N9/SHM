/**
 * Vitest 配置
 *
 * 配置文件在 server/ 目录下，vitest 命令在 server/ 目录中运行。
 * 测试套件启动时 setupFiles 会加载 __tests__/setup.js，
 * 自动切换为测试数据库并执行迁移。
 */
const { defineConfig } = require('vitest/config');

module.exports = defineConfig({
  test: {
    globals: true,
    setupFiles: ['./__tests__/setup.js'],
    environment: 'node',
    include: ['__tests__/**/*.test.js'],
    testTimeout: 10000,
    // 测试文件顺序执行（共享同一测试数据库，并行会导致 DDL 冲突）
    fileParallelism: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'html'],
      include: ['src/**/*.js'],
      exclude: ['src/app.js'],
    },
  },
});
