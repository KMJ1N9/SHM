// Commitlint 配置 — 校园二手交易小程序
// 来源：rules/git-rules.md — 只允许 feat/fix/refactor/docs/test/style
module.exports = {
  extends: [],
  rules: {
    'type-enum': [2, 'always', [
      'feat',
      'fix',
      'refactor',
      'docs',
      'test',
      'style',
    ]],
    'type-case': [2, 'always', 'lower-case'],
    'subject-case': [0],
    'subject-min-length': [2, 'always', 1],
  },
  parserPreset: {
    parserOpts: {
      headerPattern: /^(\w+):\s*(.*)$/,
    },
  },
}
