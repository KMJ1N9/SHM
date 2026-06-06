# evals/

本项目 AI 输出质量评估数据集目录。

## 目录结构

```
evals/
├── README.md                   ← 本文件
├── golden/                     ← Golden Dataset（核心功能黄金标准）
├── edge-cases/                 ← 边界 Case 数据集
├── regression/                 ← 回归测试数据集
└── prompts/                    ← Prompt 评估数据集
```

## 使用场景

本目录服务于 Claude Code 辅助开发过程中的质量保证：

1. **代码生成质量** — 生成的代码是否通过 lint + build + test
2. **规范遵守度** — 是否遵守 CLAUDE.md 中定义的 14 份编码规范
3. **Prompt 稳定性** — Prompt 修改后是否仍能产出符合预期的代码
4. **回归防护** — 新代码是否影响已有模块

## 评估维度

| 维度 | 检查项 | 依据 |
|------|--------|------|
| 文件规范 | 单文件 ≤ 500 行 | rules/file-rules.md |
| 函数规范 | 单函数 ≤ 80 行、参数 ≤ 5 个 | rules/function-rules.md |
| 命名规范 | camelCase / PascalCase / UPPER_CASE / kebab-case | rules/coding-standards.md |
| API 规范 | RESTful + 统一 `{code, message, data}` 格式 | rules/api-rules.md |
| 安全规范 | 输入校验、参数化查询、无硬编码密钥 | rules/security-rules.md |
| 错误处理 | 无空 catch、统一错误格式、含 traceId | rules/error-handling-rules.md |
| UI 规范 | 设计 Token 使用、无内联样式泛滥 | rules/ui-rules.md |
| TypeScript | strict mode、禁止 any | rules/coding-standards.md |

## 评估流程

1. 将测试用例（代码片段 + 需求描述）放入对应目录
2. 在 Claude Code 会话中提交测试用例
3. 检查输出是否通过上述评估维度
4. 记录通过/失败结果，更新 `regression/` 数据集
