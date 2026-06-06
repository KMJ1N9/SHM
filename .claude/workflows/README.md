# .claude/workflows/

本项目 Claude Code Workflow 脚本目录。所有 Workflow 编写和调用以本目录为准。

## 什么是 Workflow

Workflow 是一段 JavaScript 脚本，通过 `agent()` / `parallel()` / `pipeline()` 等 API 编排多个 Claude Code 子 Agent 协作完成复杂任务。

详见 [Workflow 工具文档](https://docs.claude.codes/zh-CN/tools/workflow)。

---

## 本项目候选 Workflow

| Workflow | 用途 | 阶段 |
|----------|------|:--:|
| `doc-audit` | 多 Agent 并行审阅文档交叉一致性 | 文档阶段 |
| `code-review-batch` | 批量代码审查（按模块并行） | 开发阶段 |
| `test-gen-batch` | 批量生成测试用例 | 测试阶段 |
| `api-cross-check` | API 文档 vs DDL vs 代码三向比对 | 验证阶段 |

---

## Workflow 编写规范

1. 脚本头部含 `export const meta = { name, description, phases }` 元数据块
2. 使用 `pipeline()` 进行多阶段无屏障流水线处理
3. 仅在真正需要汇总所有前置结果时才使用 `parallel()` 屏障
4. 每个 `agent()` 调用必须指定 `label` 和 `phase`

---

## 版本管理

每个 Workflow 独立版本号（参考 [VERSIONING.md](../../docs/VERSIONING.md)）。

## 运行方式

```bash
# 在 Claude Code 会话中通过 Workflow 工具调用
# 或使用 /workflows 交互式选择
```
