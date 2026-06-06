---
name: rules-summary
description: rules/ 14 份编码规范核心约束速查
metadata:
  type: reference
---

# 编码规范核心约束

来源：rules/ 目录 14 份规范文件。详见 [rules/README.md](../rules/README.md)。

## 红线（违反即阻断）

- **禁止 `any` 类型** — 优先 interface，明确返回值类型
- **禁止 `SELECT *`** — 必须明确字段列表
- **禁止空 catch** — `catch (e) {}` 不允许，必须输出错误日志
- **禁止硬编码密钥** — 所有密钥在 .env 中管理
- **禁止 `console.log` 泛滥** — 使用 winston logger
- **禁止无索引查询 / N+1 查询 / 循环内查询**
- **禁止内联样式泛滥** — 以 tokens.scss 为准
- **禁止伪代码 / TODO 不实现** — 输出完整可运行代码
- **禁止擅自重构 / 升级依赖 / 修改数据库结构 / 修改 API 格式**

## 量化约束

| 约束 | 上限 |
|------|:--:|
| 单函数行数 | ≤ 80 行 |
| 单文件行数 | ≤ 500 行 |
| 函数参数 | ≤ 5 个 |
| Commit type | 仅 6 种：feat/fix/refactor/docs/test/style |

## AI 行为准则（§1-4）

1. **Think Before Coding** — 不确定就问，不隐藏困惑
2. **Simplicity First** — 用最少代码解决问题，不过度设计
3. **Surgical Changes** — 只改必须改的，不顺手改相邻代码
4. **Goal-Driven Execution** — 先定义验证标准，循环直到通过

**Why:** 14 份规范的核心约束汇总，避免每次翻 rules/ 目录。

**How to apply:** 编写任何代码前过一遍红线清单。代码审查时对应用此表逐项检查。
