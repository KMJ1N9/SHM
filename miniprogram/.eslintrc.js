// ESLint 配置 — 校园二手交易小程序
// 来源：rules/coding-standards.md + rules/ai-behavior-rules.md
module.exports = {
  root: true,
  env: {
    browser: true,
    es2021: true,
    node: true,
  },
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended',
  ],
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
  },
  rules: {
    // 编码规范对齐 rules/coding-standards.md
    'no-var': 'error',
    'prefer-const': 'error',
    'camelcase': ['error', { properties: 'never' }],
    'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    'no-console': 'warn',
    'no-debugger': 'error',

    // Vue 规范
    'vue/multi-word-component-names': 'off',  // uni-app 单文件组件例外
    'vue/component-name-in-template-casing': ['error', 'PascalCase'],
    'vue/html-indent': ['error', 2],
    'vue/max-attributes-per-line': ['warn', { singleline: 3, multiline: 1 }],

    // 通用质量
    'no-duplicate-imports': 'error',
    'no-template-curly-in-string': 'error',
  },
}
