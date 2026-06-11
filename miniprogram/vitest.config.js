/**
 * vitest 配置 — 校园二手交易小程序前端测试
 *
 * 用法：
 *   npx vitest run          # 单次运行
 *   npx vitest              # 监听模式
 *   npx vitest run --coverage  # 覆盖率（需安装 @vitest/coverage-v8）
 */

import { defineConfig } from 'vitest/config';
import { resolve } from 'path';

export default defineConfig({
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },

  test: {
    // 测试目录
    include: ['__tests__/**/*.test.js'],

    // 全局设置：注入 uni mock
    globals: true,

    // 环境（jsdom 可选，当前测试无 DOM 依赖）
    // environment: 'jsdom',
  },
});
