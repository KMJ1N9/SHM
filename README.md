# 校园二手交易小程序

> 广州应用科技学院肇庆校区 C2C 二手交易平台 — 微信小程序（MVP v1.0）

**状态：** 第 1~13 轮全部完成 ✅，测试 20 文件 236 用例全过（后端 207 + 前端 29），CI/CD 就绪 | **开发：** 单人 | **周期：** 13 轮迭代

---

## 一句话介绍

帮助广应科肇庆校区学生，通过校内 C2C 二手交易平台，实现安全、可信的闲置物品面交换。核心是"手机号实名 + 站内 IM 留证 + 双向评价信誉体系"。

---

## 技术栈

| 层 | 选型 | 说明 |
|----|------|------|
| 小程序框架 | uni-app (Vue 3) | HBuilder X 开发，单人友好 |
| 后端 | Node.js + Express | 5 层结构（routes/controllers/services/repository/models） |
| 数据库 | MySQL + mysql2 | 连接池 + 参数化查询，原生 SQL 无 ORM 抽象层 |
| 即时通讯 | 腾讯云 IM SDK | 免费档 100 日活，Provider 抽象层可替换 |
| 文件存储 | 腾讯云 COS | STS 临时密钥，前端直传 |
| 部署 | 腾讯云轻量服务器 + PM2 | Nginx 反向代理，fork 模式 |
| 日志 | winston | 按天切割，3 级 logger |
| 测试 | vitest + supertest | MySQL 测试数据库 |

---

## 项目结构

```
campus-market/
├── miniprogram/          ← uni-app 小程序（Vue 3）
│   ├── pages/            ← 25 个页面（登录/首页/商品/搜索/聊天/订单/用户/举报/管理/协议/错误/通知/互评/设置/关于）
│   ├── components/       ← 6 个通用组件（ProductCard/FilterSidebar/ImageUploader/...）
│   ├── api/              ← 接口封装（auth/product/order/review/report/admin）
│   ├── store/            ← Pinia 状态管理
│   └── utils/            ← IM 初始化 + COS 上传
├── server/               ← Express 后端
│   └── src/
│       ├── routes/       ← 10 个路由模块
│       ├── controllers/  ← 10 个控制器
│       ├── services/     ← 12 个服务（含 IM Provider 抽象层）
│       ├── repository/   ← 6 个数据访问层
│       ├── models/       ← mysql2 连接池 + 表定义
│       ├── middleware/    ← 7 个中间件（JWT/权限/校验/限流/错误处理/日志）
│       ├── config/       ← 环境变量集中管理
│       └── utils/        ← 9 个工具模块（COS/微信/IM/错误码/日志/缓存/敏感词/性能）
└── docs/                 ← 项目文档（9 份核心 + 8 份辅助 + 3 份法律）
```

---

## 快速开始

> 📖 **详细步骤请参阅：[开发环境搭建指南](docs/开发环境搭建指南.md)**（含外部服务开通、常见问题排查、多设备协作等完整说明）

### 环境要求

- **Node.js** ≥ 18.x
- **MySQL** ≥ 8.0
- **HBuilder X** + **微信开发者工具**
- **腾讯云账号**（COS + IM + 微信小程序）

### 5 分钟快速启动

```bash
git clone <repo-url> && cd campus-market/server
npm install
cp .env.example .env.development   # 编辑填入真实密钥 + MySQL 连接信息
mysql -u root -p -e "CREATE DATABASE campus_market_dev"
npm run db:migrate                  # 执行建表迁移
npm run db:seed                     # 插入种子数据
npm run dev                         # http://localhost:3000
```

HBuilder X 打开 `miniprogram/` → 配置 AppID → 运行到微信开发者工具。

### 运行测试

```bash
cd server
npx vitest run              # 全量测试 207 用例（< 30s）
npx vitest                  # 监听模式
npx vitest run --coverage   # 覆盖率报告
```

---

## 文档索引

### 核心设计文档

| 文档 | 说明 | 适合谁读 |
|------|------|---------|
| [PRD-校园二手交易小程序.md](docs/PRD-校园二手交易小程序.md) | 产品需求文档 v1.0：用户体系、核心功能、MVP 边界、商业模式、风险、MVP 验证标准（11 项指标×4 层） | 所有人 |
| [技术架构文档.md](docs/技术架构文档.md) | 技术设计总集：14 张表 DDL、43 个 API 端点、30 个错误码、IM 协议、迁移策略、部署、测试策略、性能基准等 23 章 | 开发者 |
| [API接口文档.md](docs/API接口文档.md) | 43 个 API 端点完整规格：请求/响应 body、Query 参数、错误码对照、订单状态流转图、前端拦截器示例 | 前后端开发者 |
| [数据库ER图.md](docs/数据库ER图.md) | 14 张表 Mermaid ER 图 + 全部字段 + 19 条关系 + 28 项索引 + 设计决策说明 | 后端开发者 |
| [UI设计系统文档.md](docs/UI设计系统文档.md) | UI 组件规范：设计令牌、14 个组件 CSS、17 个页面布局、动效、无障碍 | 前端开发者 |

### 工程文档

| 文档 | 说明 | 适合谁读 |
|------|------|---------|
| [开发环境搭建指南.md](docs/开发环境搭建指南.md) | 从零搭建开发环境：账号注册、外部服务开通、安装配置、常见问题排查 | **新成员 / 首次搭建** |
| [测试计划.md](docs/测试计划.md) | 134 条测试用例（92 单元 + 29 集成 + 13 定时任务）、错误码覆盖检查表、PRD 可追溯性矩阵 | 开发者 / QA |
| [运维手册.md](docs/运维手册.md) | 12 章 + 2 附录：部署流程（含迁移+回滚）、PM2/Nginx 配置、定时任务、备份恢复（GFS 模型）、监控告警、故障处理 SOP（6 场景） | 运维 |

### 法律合规文件

| 文档 | 说明 | 适合谁读 |
|------|------|---------|
| [legal/用户协议.md](docs/legal/用户协议.md) | 用户服务协议：14 章，含交易规则、信誉分体系、违规处理（生效日期 2026-06-04） | 合规审查 / 用户 |
| [legal/隐私政策.md](docs/legal/隐私政策.md) | 隐私政策：信息收集、第三方 SDK 披露、用户权利、存储期限（生效日期 2026-06-04） | 合规审查 / 用户 |
| [legal/免责声明.md](docs/legal/免责声明.md) | 免责声明：平台角色、交易风险、私下交易免责、赔偿上限（生效日期 2026-06-04） | 合规审查 / 用户 |

### 项目治理

| 文档 | 说明 | 适合谁读 |
|------|------|---------|
| [CHANGELOG.md](docs/CHANGELOG.md) | 变更日志（Keep a Changelog 格式）：含文档增/改/修 + PRD 增强 + UI 新页面等完整记录 | 所有人 |
| [VERSIONING.md](docs/VERSIONING.md) | 7 类产物（Prompt/Skill/Workflow/Eval/CLAUDE.md/rules/文档）的版本格式、MAJOR/MINOR 规则、CHANGELOG 联动、Git 回滚策略 | 项目管理者 |
| [adr/](docs/adr/README.md) | **5 份已写入：** MySQL 原生 SQL / 腾讯云 IM / JWT 双 Token / Express 5 层 / uni-app Vue 3。含替代方案对比表 + 正负面后果 | 开发者 |
| [tech-debt.md](docs/tech-debt.md) | **4 项已识别：** uni-app alpha 风险(P1) + products JSON 字段(P2) + reports JSON 字段(P2) + 偏移分页迁移(P2)。含优先级矩阵 + 季度审计计划 | 开发者 |
| [prompt-failures.md](docs/prompt-failures.md) | **3 条已记录：** 版本号捏造 / 空壳文档 / 旧产物清理。每条含失败版 vs 优化版对照 + 经验总结 | AI 协作者 |
| [postmortems/](docs/postmortems/README.md) | Bug 复盘模板 + 5-Why 根因分析框架（预置，待开发/测试阶段启用） | 开发者 |
| [postmortems/incidents.md](docs/postmortems/incidents.md) | 事故记录模板 + P0-P3 等级定义 + 时间线 + 改进项追踪（预置，待上线后启用） | 运维 |

### 参考与审阅

| 文档 | 说明 | 适合谁读 |
|------|------|---------|
| [文档审阅报告-第七轮.md](docs/文档审阅报告-第七轮.md) | 第七轮全量审阅：14 份文档逐行通读 + 六维交叉比对，综合评分 8.8/10，发现 14 项问题（P0×3/P1×6/P2×5） | 项目管理者 |
| [完整开发.md](docs/完整开发.md) | AI-Native 开发方法论（12 阶段通用框架，非项目特定） | 参考 |

---

## 开发路线（13 轮迭代）

| 轮次 | 内容 | 核心交付 | 状态 |
|:--:|------|------|:--:|
| 1 | 项目骨架 | Express 5 层目录 + MySQL 建表 + uni-app 空页面路由 | ✅ |
| 2 | 注册/登录 | 微信手机号授权 + JWT 双 Token + 限流 | ✅ |
| 3 | 商品发布与浏览 | 多图上传（COS STS）+ 瀑布流 + 发布校验 | ✅ |
| 4 | 搜索 + 筛选 | MySQL FULLTEXT 全文检索 + 侧边栏筛选（分类/成色/价格） | ✅ |
| 5 | 站内 IM | 腾讯云 IM 接入 + 系统消息 + 通知中心 | ✅ |
| 6 | 交易流程 | 订单状态机 + 面交确认 + 双向互评 + 信誉分联动 | ✅ |
| 7 | 举报 + 客服工单 | 举报表单 + 工单流转（受理→裁决）+ 信誉分扣减 | ✅ |
| 8 | 管理后台 | 用户管理 + 商品下架 + 数据看板 + 审计日志 + 敏感词库 | ✅ |
| 9 | 收尾补全 | 编辑资料 + 个人主页 + 设置 + 关于 + 404/网络异常 + P1 修复 7 项 | ✅ |
| 10 | 工程化基础 | winston 日志 26 点 + console.log 清零 + bare Error 清零 + 手机号脱敏 | ✅ |
| 11 | 缓存 + 性能 | LRU 缓存接入（Cache-Aside）+ 游标分页 + 慢查询日志 + 图片懒加载 | ✅ |
| 12 | 前端收尾 | 协议内容页 + 编辑商品双模式 + 游标分页扩展 + EmptyState 组件化 + 29 前端测试 | ✅ |
| 13 | 测试 + CI + 部署 | 55 后端测试用例 + GitHub Actions CI + nginx + 压力测试基线 + Ship-Gate 89 项审计 + 审核材料 | ✅ |

---

## 前置设计完成清单

- [x] PRD（产品需求文档）
- [x] 技术架构文档（23 章，含 DB/API/IM/部署/测试/性能基准）
- [x] UI 设计系统文档（设计令牌 + 组件规范 + 页面布局）
- [x] 法律合规文件（用户协议 + 隐私政策 + 免责声明）
- [x] 环境变量规范（25+ 变量，3 环境对比）
- [x] IM 消息协议（4 种系统消息 JSON Schema）
- [x] 敏感词库规范（DFA 算法 + 9 类词库 + 管理 API）
- [x] **代码开发（第 1~13 轮全部完成）** ← v1.0 编码完结
- [x] **测试 236 用例全绿（后端 207 + 前端 29）**
- [x] **CI/CD 流水线就绪（GitHub Actions 4 Job）**
- [x] **Ship-Gate 89 项审计通过（⚠️ Go with Warnings）**
- [x] 前端大文件拆分（3 文件超 500 行 → 全部 ≤500 行，提取 8 组件 + 2 composable + 1 工具函数）
- [ ] 生产 Linux 服务器部署验证
- [ ] 全链路真机烟雾测试
- [ ] 微信小程序提交审核

---

## 许可证

本项目为个人学习与校园服务项目，暂未选定开源许可证。
