# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## 项目概要

校园二手交易小程序（广州应用科技学院肇庆校区 C2C 二手交易平台）。**技术栈：** uni-app (Vue 3) + Pinia + SCSS → 微信小程序 / Node.js + Express 5 层架构 + MySQL + mysql2（无 ORM）/ 腾讯云 IM + COS / vitest + supertest / winston / PM2。

**当前阶段：** 前置设计完成，准备开始编码（13 轮迭代，见 README.md）。

---

## 常用命令

### 前端（miniprogram/）

```bash
cd miniprogram
npm run dev:mp-weixin    # 编译 uni-app → 微信小程序（开发模式，热更新）
npm run build:mp-weixin  # 生产构建
npm run lint             # ESLint 检查（.js + .vue）
npm run lint:fix         # ESLint 自动修复
npm run format           # Prettier 格式化
```

HBuilder X 打开 `miniprogram/` → 配置 AppID → 运行到微信开发者工具。或 CLI：`npx @dcloudio/uvm` 管理 uni-app 编译器版本。

### 后端（server/）— 待编码

```bash
cd server
npm install
cp .env.example .env.development   # 编辑填入密钥 + MySQL 连接信息
mysql -u root -p -e "CREATE DATABASE campus_market_dev"
npm run db:migrate                  # 建表迁移
npm run db:seed                     # 种子数据
npm run dev                         # http://localhost:3000
npx vitest run                      # 全量测试
npx vitest run --coverage           # 覆盖率
npx vitest                          # 监听模式
```

---

## 架构概览

### Express 5 层架构（server/src/）

```
routes/ → controllers/ → services/ → repository/ → models/
  (路由)     (控制器)      (业务逻辑)    (数据访问)    (mysql2 连接池)
```

- **routes/** — 10 个路由模块，只做路由注册和中间件绑定
- **controllers/** — 10 个控制器，只做参数提取和响应组装，无业务逻辑
- **services/** — 12 个服务，所有业务逻辑在此层。含 IM Provider 抽象层（可替换 IM 厂商）
- **repository/** — 6 个数据访问层，封装 SQL，业务层不接触 SQL。禁止 `SELECT *`
- **models/** — mysql2/promise 连接池，原生参数化 SQL
- **middleware/** — 7 个中间件：JWT 鉴权、角色权限、参数校验（Joi）、限流（令牌桶）、错误处理、请求日志
- **config/** — 环境变量集中管理（.env.development / .env.staging / .env.production）
- **utils/** — 9 个工具模块：COS STS、微信 API、IM REST、错误码、winston 日志、LRU 缓存、DFA 敏感词、性能监控

### uni-app 页面结构（miniprogram/）

```
pages/          ← 25 个页面（登录/首页/商品/搜索/聊天/订单/用户/举报/管理/错误/互评/通知/设置/关于）
components/     ← 6 个通用组件（ProductCard/FilterSidebar/ImageUploader/StarRating/AppNavbar/EmptyState）
api/            ← 6 个接口封装模块（auth/product/order/review/report/admin）
store/          ← 3 个 Pinia store（user/chat/app）
utils/          ← IM 初始化 + COS 上传
styles/         ← tokens.scss（设计令牌） + common.scss
```

### 认证流程

JWT 双 Token：Access Token（短期）+ Refresh Token（长期）。登录时微信手机号授权 → 服务端验证 → 签发双 Token。Access Token 过期 → Refresh Token 换新。无感刷新在前端拦截器中实现。

### 核心数据流（详见 docs/技术架构文档.md §一 DFD）

- 用户请求 → HTTPS → Express（鉴权 + 校验 + 敏感词过滤） → MySQL
- 聊天消息 → WebSocket → 腾讯云 IM（直连，不经 Express）
- 图片上传 → 服务端发 STS 临时密钥 → 前端直传 COS（不经 Express）
- 系统消息 → Express → IM REST API → 推送到用户

### 关键设计决策（详见 docs/adr/）

| ADR | 决策 | 原因 |
|-----|------|------|
| 0001 | MySQL 原生 SQL，无 ORM | 排查零黑盒，开发者对 SQL 更熟悉 |
| 0002 | 腾讯云 IM | 自建 IM 需 2 周，SDK 半天接入 |
| 0003 | JWT 双 Token | 安全性与用户体验平衡 |
| 0004 | Express 5 层 | 关注点分离，单层可独立测试 |
| 0005 | uni-app Vue 3 | 单人开发友好，一套代码多端 |

---

## 关键文件索引

| 文件 | 用途 | 何时读 |
|------|------|--------|
| `miniprogram/pages.json` | 页面路由配置 | 新增/修改页面时 |
| `miniprogram/styles/tokens.scss` | 设计令牌（颜色/间距/字号） | UI 开发必读 |
| `miniprogram/.eslintrc.js` | ESLint 规则 | lint 问题排查 |
| `server/.env.example` | 环境变量模板（25+ 变量） | 配置环境时 |
| `docs/技术架构文档.md` | 完整技术设计（14 表 DDL + 43 API + 30 错误码） | 后端开发必读 |
| `docs/API接口文档.md` | 43 个 API 端点完整规格 | 前后端联调 |
| `docs/数据库ER图.md` | 14 表 ER 图 + 19 关系 + 28 索引 | 数据库操作 |
| `docs/UI设计系统文档.md` | UI 组件/页面/动效规范 | 前端开发必读 |
| `docs/PRD-校园二手交易小程序.md` | 产品需求文档 | 理解功能边界 |
| `docs/测试计划.md` | 134 条测试用例 | 写测试时参考 |
| `docs/运维手册.md` | 部署/备份/监控/故障处理 | 部署时 |
| `rules/README.md` | 14 份规范文件总览 | 了解编码约束 |

---

## 行为准则

校园二手交易小程序 — AI 辅助开发行为规范。通用指南 + rules/ 全部 14 份规范核心约束。

**Tradeoff:** 规范偏向稳妥优先。简单任务可灵活判断。

---

## 1. Think Before Coding

**不要假设。不要隐藏困惑。主动暴露权衡。**

动手前：
- 明确陈述假设。不确定就问。
- 多种理解时列出选项，不要默默选一个。
- 有更简单的方案就说。该 push back 就 push back。
- 遇到不清楚的，停下来，指出哪里不清楚，然后问。

## 2. Simplicity First

**用最少代码解决问题。不写 speculative 代码。**

- 不写超出需求的功能。
- 不为单次使用创建抽象。
- 不写没人要的"灵活性"或"可配置性"。
- 不为不可能的场景做错误处理。
- 写了 200 行能缩减到 50 行 → 重写。

自问："高级工程师会觉得这过度设计吗？" 是 → 简化。

## 3. Surgical Changes

**只改必须改的。只清理自己弄脏的。**

编辑已有代码时：
- 不要"顺手改进"相邻代码、注释、格式。
- 不要重构没坏的东西。
- 匹配已有风格，即使和你习惯不同。
- 发现无关死代码 → 提出来，不要直接删。

你的改动产生孤儿时：
- 删除你的改动导致不再使用的 import/变量/函数。
- 不删除改动前就存在的死代码（除非要求）。

检验标准：每行改动都能追溯到用户请求。

## 4. Goal-Driven Execution

**定义成功标准。循环直到验证通过。**

把任务转化为可验证目标：
- "加校验" → "先写无效输入测试，再让它通过"
- "修 bug" → "先写复现测试，再让它通过"
- "重构 X" → "确保重构前后测试都通过"

多步骤任务给简要计划：
```
1. [步骤] → 验证: [检查项]
2. [步骤] → 验证: [检查项]
```

---

## 5. 项目工程规范（rules/ 全部 14 份）

### 5.1 命名规范（coding-standards）

| 对象 | 规范 | 示例 |
|------|------|------|
| 变量/函数 | camelCase | `userName`, `getOrderList` |
| 类/组件 | PascalCase | `UserService`, `ProductCard` |
| 常量 | UPPER_CASE | `MAX_PAGE_SIZE` |
| 前端文件 | kebab-case | `product-card.vue`, `api-error.js` |
| 数据库表/字段 | snake_case | `user_info`, `created_at` |

### 5.2 TypeScript 规范（coding-standards）

- 开启 strict mode
- **禁止 `any`** — 优先 `interface`，明确返回值类型
- 避免类型断言滥用

### 5.3 函数规范（function-rules）

- 单函数 ≤ **80 行**
- 参数 ≤ **5 个**
- 单一职责；复杂逻辑必须拆分

### 5.4 文件规范（file-rules）

- 单文件 ≤ **500 行**，超过必须拆分

### 5.5 API 规范（api-rules）

- RESTful：`GET /users` `POST /users` `PUT /users/:id` `DELETE /users/:id`
- **统一返回格式**：`{ code: 0, message: "success", data: {} }`
- 禁止返回格式不一致的响应

### 5.6 数据库规范（database-rules）

- 所有表必须含：`id`, `created_at`, `updated_at`
- 禁止：`SELECT *`、无索引查询、N+1 查询、循环内查询
- 必须：明确字段、JOIN 替代循环、适当索引、分页查询

### 5.7 安全规范（security-rules）

- 校验所有用户输入
- 参数化查询（防 SQL 注入）
- 敏感信息加密、JWT 校验
- 防 XSS / CSRF
- **禁止**：硬编码密钥、日志输出密码、明文存储敏感信息

### 5.8 错误处理规范（error-handling-rules）

- **禁止空 catch** `catch (e) {}`
- 必须：输出错误日志、返回统一错误格式、携带 traceId
- 日志禁止：`console.log` 到处输出、打印敏感数据、无意义日志

### 5.9 性能规范（performance-rules）

- 减少重复渲染、避免重复请求
- 合理缓存、懒加载大型模块、控制包体积
- 禁止：巨型组件、巨型函数

### 5.10 测试规范（test-rules）

- 提交前必须：**lint 通过 + build 成功 + 单元测试通过**
- 新增业务逻辑必须包含测试

### 5.11 Git 规范（git-rules）

- 只允许 6 种 type：`feat` `fix` `refactor` `docs` `test` `style`
- 格式：`feat: 添加用户登录功能`

### 5.12 注释规范（comment-rules）

- 必须解释：**为什么**这样做、业务背景、边界条件
- **禁止废话**：`// 定义用户变量` 这类不写

### 5.13 UI 规范（ui-rules）

- 统一设计风格，控制颜色数量（以 tokens.scss 为准）
- 间距统一（8pt 系统）、适配移动端
- 禁止：随意使用颜色、弹窗套弹窗、内联样式泛滥

### 5.14 AI 行为约束（ai-behavior-rules + ai-output-rules）

**写代码前必须：**
1. 阅读相关文件 → 理解现有架构 → 理解已有实现方式
2. 优先复用现有代码
3. 保持风格一致
4. 最小化修改范围

**禁止：**
- 擅自重构项目 / 升级依赖 / 修改数据库结构 / 修改 API 格式
- 擅自移动文件 / 重命名模块 / 引入新框架
- 创建重复功能 / 写"临时方案" / 留 TODO 不实现
- 生成伪代码替代真实实现
- "优化"没问题的代码 / 大规模重构 / 重写已有稳定逻辑

**输出要求：**
- 完整可运行代码，不省略关键实现
- 不生成伪代码

---

## 6. Skills 调用规则（.claude/skills/）

本项目安装了 **6 个技能包**（共 540+ skill）。核心原则：**先查场景路由表（§6.7），再按需深入具体 skill。**

**本项目技术栈是 uni-app (Vue 3) + SCSS + Node.js/Express + MySQL，非 React + Tailwind。任何 skill 调用前必须判断技术栈兼容性。**

---

### 6.0 六大技能包总览

| 包名 | 版本 | 类型 | 核心价值 | 技能数 |
|------|------|------|---------|:--:|
| **ui-ux-pro-max** | v2.5.0 | UI/UX 设计 | 50+ 风格/161 调色板/57 字体/99 UX 指南 | 7 |
| **superpowers** | v5.1.0 | 开发方法论 | TDD/调试/Code Review/Plan→Execute→Verify | 13 |
| **claude-skills-main** | v2.6.1 | 工程技能库 | 后端/数据库/API/测试/安全/PR 审查/性能 | 272 |
| **everything-claude-code** | v2.0.0-rc.1 | 开发基础设施 | MySQL/Express 专项 + 60 Agent + 75 命令 + Hook 运行时 | 229 |
| **anthropics-skills** | — | 官方示例 | 少量可用（API 集成/文档/设计方法论） | 17 |
| **andrej-karpathy-skills** | v1.0.0 | 行为准则 | **已完全融入本项目 §1-4，无需单独调用** | 1 |

---

### 6.1 ui-ux-pro-max — UI/UX 设计智能 🔴

**定位：** 本项目 UI 开发的核心技能包。所有视觉设计工作的第一站。

**触发条件（任一即调用）：**
- 设计新页面、创建/重构 UI 组件、选择配色/字体/间距
- UI 看起来不够专业但说不清原因
- 审查 UX 问题、可访问性、视觉一致性
- 实现动效、响应式布局、暗色模式

**不适用：** 纯后端逻辑、API/数据库设计、非视觉脚本

**工作流（必须按顺序）：**
```bash
# Step 1: 生成设计系统（强制第一步）
python3 .claude/skills/ui-ux-pro-max-skill-main/.claude/skills/ui-ux-pro-max/scripts/search.py \
  "<产品类型> <风格关键词>" --design-system -p "校园二手交易"

# Step 2: 按需补充
python3 .../search.py "<关键词>" --domain ux         # UX 最佳实践
python3 .../search.py "<关键词>" --domain style      # 风格选项
python3 .../search.py "<关键词>" --domain color      # 调色板
python3 .../search.py "<关键词>" --domain typography # 字体
```

**子技能分级（ui-ux-pro-max 内部 7 个 skill）：**
- 🔴 `ui-ux-pro-max` — UI/UX 核心，频繁调用
- 🟡 `design-system` — SCSS 令牌架构设计时调用（注意：三层架构思想可用，但不要生搬 Tailwind）
- 🟢 `brand` — 品牌要素管理时调用
- 🟢 `design` — 需要 Logo/图标时调用（跳过 banner/slides/CIP 子路由）
- ⚫ `ui-styling` — **禁止。** 100% React + shadcn/ui + Tailwind，与 uni-app + SCSS 不兼容
- ⚫ `banner-design` — 禁止。社交媒体广告，非小程序开发
- ⚫ `slides` — 禁止。HTML 演示文稿，非小程序开发

---

### 6.2 superpowers — 开发方法论 🔴

**定位：** 软件工程流程规范。控制"怎么开发"——从需求到合入的全链路。

**Tier 1 — 频繁调用：**

| Skill | 触发场景 | 关键规则 |
|-------|---------|---------|
| `systematic-debugging` | **任何 Bug**，前后端都适用 | 4 阶段：根因→模式→假设→实现。3+ 次修复 = 架构问题 |
| `verification-before-completion` | **每次声称完成前** | 铁律：无新鲜验证输出 = 不算完成。禁止"应该能工作" |
| `test-driven-development` | **新增后端逻辑/composable/工具函数** | 红→绿→重构。禁止无测试写生产代码 |
| `requesting-code-review` | **每个 feature 完成后、合入前** | 传 git diff + 需求 + rules/ 上下文给审查 Agent |
| `receiving-code-review` | **每次收到审查反馈** | 先验证再改。该 push back 就 push back。禁止表演性同意 |

**Tier 2 — 特定场景：**

| Skill | 触发场景 |
|-------|---------|
| `brainstorming` | 新 feature 设计探索。小修复跳过 |
| `writing-plans` | 跨前后端多步骤 feature |
| `finishing-a-development-branch` | Feature 分支收尾（4 选项：本地合入/PR/保留/丢弃） |
| `using-git-worktrees` | 多 commit feature 需要隔离工作区时 |
| `dispatching-parallel-agents` | 真正独立的并行问题（如前端 bug + 后端 bug 无关联） |

**Tier 3 — 稀有场景：** `subagent-driven-development`、`executing-plans`（仅在后台任务的全自动执行时用）、`writing-skills`（创建新 skill 时用，极罕见）

---

### 6.3 claude-skills-main — 工程技能库 🔴🟡

**定位：** 272 个生产级技能，覆盖 9 个领域。本项目只用 engineering-team/ 和 engineering/ 中的后端/数据库/测试/审查技能，以及少量 product-team/ 技能。

**Tier 1 — 频繁调用（后方开发核心）：**

| Skill | 路径 | 触发场景 |
|-------|------|---------|
| `senior-backend` | engineering-team/ | Node.js/Express API 设计、中间件、JWT、限流 |
| `sql-database-assistant` | engineering/ | MySQL 查询优化、EXPLAIN、迁移生成、N+1 检测 |
| `database-schema-designer` | engineering/ | 新增/修改表结构、ER 图、索引设计 |
| `api-design-reviewer` | engineering/ | API 设计审查（命名/分页/错误码/版本化） |
| `api-test-suite-builder` | engineering/ | 自动扫描 Express 路由生成 Vitest+Supertest 测试 |
| `code-reviewer` | engineering-team/ | 自动化 PR 审查：秘密检测/SQL 注入/any 类型/长函数 |
| `pr-review-expert` | engineering/ | 深度 PR 审查：blast radius/安全/breaking change/N+1 |
| `spec-driven-workflow` | engineering/ | 先写 spec 再写代码（6 阶段：需求→spec→验证→测试→实现→自查） |
| `tdd-guide` | engineering-team/ | TDD 方法指导（支持 Vitest/Jest/Mocha） |
| `ship-gate` | engineering/ | **发版前审计**：89 项检查覆盖安全/DB/部署/代码质量/前端/依赖 |

**Tier 2 — 特定场景：**

| Skill | 触发场景 |
|-------|---------|
| `senior-architect` | 架构设计/重构决策/依赖分析时 |
| `senior-security` | 安全审计/威胁建模/STRIDE 分析时 |
| `focused-fix` | Feature 修复（5 阶段：Scope→Trace→Diagnose→Fix→Verify） |
| `performance-profiler` | Node.js 性能瓶颈排查（CPU/内存/慢查询/包体积） |
| `dependency-auditor` | 定期依赖审计（CVE/许可证冲突/过期包） |
| `tech-debt-tracker` | 技术债识别和跟踪 |
| `env-secrets-manager` | .env 规范/密钥泄露检测/密钥轮换 |
| `adversarial-reviewer` | 关键 PR 的对抗性审查（3 角色：破坏者/新人/安全审计） |
| `changelog-generator` | 从 Conventional Commits 生成 CHANGELOG |
| `codebase-onboarding` | 生成新人入职文档 |
| `self-eval` | AI 工作质量校准（双轴评分 + 反膨胀检测） |
| `product-manager-toolkit` | RICE 优先级排序/PRD 模板（单人开发自我管理用） |

**Tier 3 — 偶尔有用：** `caveman`（token 压缩）、`grill-me`（方案质问）、`handoff`（跨 session 交接）、`migration-architect`（大型 DB 迁移）、`tech-stack-evaluator`（技术选型）

**⚫ Tier 4 — 跳过：** marketing-skill/、c-level-advisor/、finance/、ra-qm-team/、business-growth/ 全部；以及 React/Next.js/Tailwind/Docker/K8s/GraphQL/Python 数据科学 相关 skill

---

### 6.4 everything-claude-code — 开发基础设施 🔴🟡

**定位：** 不是单纯的 skill 包，而是 Claude Code 的"开发操作系统"。提供 60 个 Agent、75 个命令、Hook 运行时、持续学习系统。**ecc 的 MySQL/Express 专项 skill 是其他包没有的。**

**Tier 1 — 频繁调用（本项目独有的高价值 skill）：**

| Skill | 触发场景 | 为什么 ecc 独有 |
|-------|---------|---------------|
| `mysql-patterns` | **任何 MySQL 操作**：schema 设计/索引/连接池/死锁/upsert/分页 | 其他包无 MySQL 专项 skill，这个有 `mysql2` 示例 |
| `backend-patterns` | **任何 Express 后端代码**：Repository/Service/Middleware 模式 | 明确标注 "Node.js, Express"，非泛用后端指南 |
| `api-design` | REST API 设计决策（命名/分页/过滤/版本化） | 含 offset 和 cursor 双分页模式 |
| `security-review` | 安全审查：OWASP/密钥管理/注入/XSS/CSRF | 针对 Web API 的安全清单 |
| `tdd-workflow` | TDD 执行：红绿重构 + git checkpoint | 执行层面（superpowers 偏方法论，ecc 偏实操） |
| `error-handling` | 错误处理设计：类型化错误/Result 模式/重试/Express 中间件 | Node.js 错误处理专项 |
| `coding-standards` | 基线规范：命名/不可变性/async-await/类型安全/代码异味 | 通用基础规范 |
| `git-workflow` | 分支策略/commit 规范/PR 模板 | Git 工作流规范 |

**Tier 2 — 特定场景：**

| Skill | 触发场景 |
|-------|---------|
| `database-migrations` | MySQL 安全迁移（expand-contract/零停机/回滚） |
| `vite-patterns` | Vite 构建配置优化（uni-app 底层用 Vite） |
| `ui-to-vue` | 将设计稿/截图批量转 Vue 3 Composition API 组件 |
| `verification-loop` | 提 PR 前全验证（build→type-check→lint→test→security→diff） |
| `design-system` | 设计系统生成/审计（颜色/间距/组件一致性） |
| `search-first` | 写代码前先查 npm/PyPI/MCP/GitHub 避免重复造轮子 |
| `codebase-onboarding` | 4 阶段分析生成新人入门指南 |
| `code-tour` | 创建 .tour 文件用于代码走读 |

**ecc 独有基础设施（非 skill，直接可用）：**
- **60 个 Agent**：code-reviewer / security-reviewer / database-reviewer / planner / tdd-guide / build-error-resolver / refactor-cleaner / doc-updater / e2e-runner / typescript-reviewer
- **75 个命令**：`/plan` `/code-review` `/build-fix` `/quality-gate` `/test-coverage` `/security-scan` `/update-docs` `/sessions`
- **Hook 运行时**：保存时自动格式化/lint、提交前质量门禁
- **持续学习 v2**：自动从 session 提取项目模式生成自定义 skill
- **自主循环**：6 种模式（流水线/PR 循环/RFC 驱动的 DAG）

---

### 6.5 anthropics-skills — 官方示例（少量可用）🟢

**定位：** Anthropic 官方 skill 示例库，大多面向文档/设计/企业通讯，只有 3 个对本项目有意义。

**Tier 2 — 特定场景：**

| Skill | 触发场景 |
|-------|---------|
| `claude-api` | 后端集成 Claude API（商品描述生成/内容审核/智能搜索/客服） |
| `frontend-design` | **设计方法论借鉴**——色彩/字体/空间/动效原则可学，但代码输出是 Web DOM，不能直接用于 uni-app |
| `skill-creator` | 创建本项目专属 skill（如 uni-app-component-builder） |

**Tier 3 — 偶尔有用：** `canvas-design`（营销海报）、`pdf`（收据/发票生成）、`xlsx`（数据导出）、`doc-coauthoring`（文档协作）、`theme-factory`（配色灵感）、`mcp-builder`（MCP 服务器开发）

**⚫ Tier 4 — 禁止/无用：** `algorithmic-art`、`brand-guidelines`（Anthropic 品牌）、`docx`、`internal-comms`、`pptx`、`slack-gif-creator`、`web-artifacts-builder`（React+Tailwind+shadcn）、`webapp-testing`（Playwright 测 Web DOM，小程序无法用）

---

### 6.6 andrej-karpathy-skills — 行为准则（已融入 §1-4）

此包的 4 条原则（Think Before Coding / Simplicity First / Surgical Changes / Goal-Driven Execution）**已完全融入本项目 CLAUDE.md §1-4**。不单独调用。本项目 §1-4 即其增强版（融入了 14 份 rules/ 的项目特化约束）。

---

### 6.7 场景路由表（核心速查）

**按任务查表选 skill：**

| 我要做什么 | 首选 skill（包） | 备选/补充 |
|-----------|----------------|----------|
| 🎨 设计新页面/组件 | `ui-ux-pro-max` (ui-ux-pro-max) | `frontend-design` (anthropics) 仅方法论 |
| 🎨 查 UX 规范（触控/对比度/动效） | `ui-ux-pro-max` (ui-ux-pro-max) | — |
| 🎨 配色/字体/间距选择 | `ui-ux-pro-max` (ui-ux-pro-max) | `design-system` (ecc) |
| 🔧 写 Express API 路由 | `senior-backend` (claude-skills) | `backend-patterns` (ecc) |
| 🔧 设计/审查 REST API | `api-design-reviewer` (claude-skills) | `api-design` (ecc) |
| 🔧 写 MySQL 查询/建表 | `mysql-patterns` (ecc) | `sql-database-assistant` (claude-skills) |
| 🔧 设计数据库 Schema | `database-schema-designer` (claude-skills) | `database-designer` (claude-skills) |
| 🔧 数据库迁移 | `database-migrations` (ecc) | `sql-database-assistant` (claude-skills) |
| 🔧 安全审查 | `security-review` (ecc) | `senior-security` (claude-skills) |
| 🔧 错误处理设计 | `error-handling` (ecc) | `senior-backend` (claude-skills) |
| 🧪 写后端测试 | `api-test-suite-builder` (claude-skills) | `tdd-guide` (claude-skills) |
| 🧪 TDD 红绿重构 | `test-driven-development` (superpowers) | `tdd-workflow` (ecc) |
| 🐛 Debug（任何层级） | `systematic-debugging` (superpowers) | — |
| ✅ 声称完成前验证 | `verification-before-completion` (superpowers) | `verification-loop` (ecc) |
| 📋 写实现计划 | `writing-plans` (superpowers) | `spec-driven-workflow` (claude-skills) |
| 📋 需求头脑风暴 | `brainstorming` (superpowers) | `product-discovery` (claude-skills) |
| 👀 Code Review（常规） | `code-reviewer` (claude-skills) | `requesting-code-review` (superpowers) |
| 👀 Code Review（关键变更） | `pr-review-expert` (claude-skills) + `adversarial-reviewer` (claude-skills) | — |
| 👀 处理审查反馈 | `receiving-code-review` (superpowers) | — |
| 🚀 发版前审计 | `ship-gate` (claude-skills) | `verification-loop` (ecc) |
| ⚡ 性能排查 | `performance-profiler` (claude-skills) | `mysql-patterns` (ecc) 慢查询 |
| 🔒 依赖安全检查 | `dependency-auditor` (claude-skills) | `env-secrets-manager` (claude-skills) |
| 📐 架构设计 | `senior-architect` (claude-skills) | `brainstorming` (superpowers) |
| 📝 生成 CHANGELOG | `changelog-generator` (claude-skills) | — |
| 🔄 Vite 构建配置 | `vite-patterns` (ecc) | — |
| 📖 新人入门 | `codebase-onboarding` (ecc) | `codebase-onboarding` (claude-skills) |

---

### 6.8 全局禁止事项

**技术栈红线（不可逾越）：**
- ❌ 从 `ui-styling` / `web-artifacts-builder` / `frontend-design` 抄 React/shadcn/Tailwind 代码到 Vue 项目
- ❌ 在 uni-app 中引入 Tailwind CSS 或任何 CSS-in-JS 方案
- ❌ 用 Web DOM 思维写小程序代码（无 `document`/`window`/DOM API）
- ❌ 调用面向 React/Angular/Next.js 的 skill 生成代码（查看 skill 描述中的技术栈标注）

**流程红线：**
- ❌ 调用 `subagent-driven-development` 或 `executing-plans` 进行全自动连续执行而不设人工检查点（违反 §5.14 AI 行为约束）
- ❌ 调用 marketing-skill / c-level-advisor / finance / ra-qm-team / business-growth 等非工程领域 skill
- ❌ 忽略 `systematic-debugging` 和 `verification-before-completion` 直接写修复代码

---

**这些规范在生效的条件：** diff 中无无关改动，无不必要抽象，疑问在实现前澄清而非出错后补救。
