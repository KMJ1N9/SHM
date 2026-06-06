# prompts/

校园二手交易小程序 — Claude Code 协作 Prompt 模板库。每份 Prompt 均可独立使用，直接粘贴给 Claude Code 即生效。

---

## 目录结构

```
prompts/
├── README.md              ← 本文件
├── code-review.md         ← 代码审查 Prompt（v1.0）
├── page-scaffold.md       ← 页面脚手架生成 Prompt（v1.0）
├── api-module.md          ← API 模块开发 Prompt（v1.0，含前后端）
├── bug-fix.md             ← Bug 修复 Prompt（v1.0）
├── refactor.md            ← 重构 Prompt（v1.0）
└── test-generation.md     ← 测试生成 Prompt（v1.0，含单元+集成）
```

---

## Prompt 速查

| Prompt | 适用场景 | 关键约束来源 |
|--------|---------|------------|
| `code-review.md` | Feature 完成后 / PR 提交前 / 用户要求审查 | rules/ 全部 14 份规范 + docs/API接口文档.md 错误码 |
| `page-scaffold.md` | 创建新页面 / 为桩代码生成完整骨架 | docs/UI设计系统文档.md + tokens.scss 设计令牌 |
| `api-module.md` | 开发新 API 端点 / 实现后端模块 / 封装前端 API | docs/技术架构文档.md 5层架构 + 30 标准错误码 |
| `bug-fix.md` | 收到 Bug 报告 / 测试失败 | systematic-debugging skill 4 阶段方法论 |
| `refactor.md` | 函数超 80 行 / 文件超 500 行 / 消除重复 / 修复跨层 | rules/function-rules.md + rules/file-rules.md |
| `test-generation.md` | 新增业务逻辑 / 补测试覆盖 | docs/测试计划.md 134 条用例规范 + rules/test-rules.md |

---

## 使用方式

**方式一（推荐）：引用 Prompt 文件**

直接告诉 Claude Code 按指定 Prompt 执行：

> "请按照 `prompts/api-module.md` 的规范，开发订单模块的完整 API（后端 5 层 + 前端封装）。"

> "请按照 `prompts/code-review.md` 的审查流程，审查当前分支的 diff。"

Claude Code 会自行读取该文件并按其中定义的流程和约束执行。

**方式二：粘贴 Prompt 内容**

1. 根据任务类型选择对应 Prompt 文件
2. 将 Prompt 内容粘贴给 Claude Code
3. 按 Prompt 中的"输入"部分提供具体参数（文件路径 / 功能描述 / 错误日志等）
4. Claude Code 将按照 Prompt 中定义的流程和规范生成/审查代码

**方式三：结合 Skill 调用**

部分 Prompt 与 `.claude/skills/` 中的 skill 互补。例如：

| 任务 | Prompt | 建议同时调用的 Skill |
|------|--------|-------------------|
| 修复 Bug | `bug-fix.md` | `systematic-debugging` (superpowers) |
| 写测试 | `test-generation.md` | `test-driven-development` (superpowers) 或 `tdd-workflow` (ecc) |
| Code Review | `code-review.md` | `code-reviewer` (claude-skills) |
| 设计新 API | `api-module.md` | `senior-backend` (claude-skills) + `api-design` (ecc) |
| 创建新页面 | `page-scaffold.md` | `ui-ux-pro-max` (ui-ux-pro-max) |
| 重构 | `refactor.md` | `code-reviewer` (claude-skills) — 重构后审查 |

---

## Prompt 编写规范

每份 Prompt 模板遵循以下标准：

1. **YAML 前置元数据**：`name`、`description`、`version`、`created`、`triggers`
2. **角色定义**：明确 AI 的角色定位和技术栈上下文
3. **输入规范**：明确用户需要提供哪些信息
4. **流程定义**：分阶段/分步骤的执行流程，每步有验证标准
5. **约束清单**：引用 `rules/` 和 `docs/` 中的具体规范，含文件路径和行级引用
6. **输出格式**：明确最终产出的格式要求
7. **验证清单**：生成后自查的 checklist

---

## 版本管理

遵循 `docs/VERSIONING.md` 定义的规则：

```
v<MAJOR>.<MINOR>

MAJOR: 不兼容的变更（输出格式变化、流程重构）
MINOR: 向后兼容的改进（措辞优化、边界条件补充、示例更新）
```

每次 Prompt 修改后更新 `docs/CHANGELOG.md`。
