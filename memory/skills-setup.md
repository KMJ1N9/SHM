---
name: skills-setup
description: 6 个 Claude Code 技能包安装记录 + 场景路由速查
metadata:
  type: reference
  installDate: 2026-06-03 ~ 2026-06-05
---

# Skills 技能包安装

**安装位置：** `.claude/skills/`
**总技能数：** 6 包 / 540+ skill
**管理策略：** 四级（🔴频繁 → 🟡场景 → 🟢偶尔 → ⚫禁止）

## 已安装的 6 个包

| 包名 | 技能数 | 核心价值 | 优先级 |
|------|:-----:|---------|:----:|
| ui-ux-pro-max | 7 | UI/UX 设计（50+ 风格/161 调色板/57 字体） | 🔴 |
| superpowers | 13 | 开发方法论（TDD/调试/Review/Plan→Verify） | 🔴 |
| claude-skills-main | 272 | 工程技能库（后端/数据库/API/测试/安全） | 🔴🟡 |
| everything-claude-code | 229 | 开发基础设施（MySQL/Express 专项 + 60 Agent） | 🔴🟡 |
| anthropics-skills | 17 | 官方示例（API 集成/文档/设计方法论） | 🟢 |
| andrej-karpathy-skills | 1 | 行为准则（已融入 CLAUDE.md §1-4） | ⚫ |

## 场景路由速查

| 任务 | 首选 Skill |
|------|----------|
| 设计新页面/组件 | `ui-ux-pro-max` |
| 写 Express API | `senior-backend` / `backend-patterns` |
| 写 MySQL 查询 | `mysql-patterns` / `sql-database-assistant` |
| 安全审查 | `security-review` / `senior-security` |
| 后端测试 | `api-test-suite-builder` / `tdd-guide` |
| Debug | `systematic-debugging` |
| Code Review | `code-reviewer` / `pr-review-expert` |
| 发版前审计 | `ship-gate` |

## 技术栈红线

- ❌ 禁止抄 React/shadcn/Tailwind 代码到 Vue 项目
- ❌ 禁止在 uni-app 中引入 Tailwind CSS 或 CSS-in-JS
- ❌ 禁止用 Web DOM 思维写小程序代码
- ❌ 禁止调用 React/Angular/Next.js 定向 skill

**Why:** 540+ skill 必须有优先级和禁止列表。技术栈冲突是本项目最高风险（uni-app + Vue ≠ React + Tailwind）。

**How to apply:** 编码前查场景路由表。详见 CLAUDE.md §6（完整规则）。
