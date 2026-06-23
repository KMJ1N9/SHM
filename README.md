# 校园二手交易小程序

> 广州应用科技学院肇庆校区 C2C 二手交易平台 — 微信小程序（MVP v1.0）

**状态：** Node.js 后端 1~13 轮全部完成 ✅ | Java 分布式后端 Phase 0~16（0~11 + 13 Seata + 16 Redisson ✅，12/14/15/17 待开始）| 测试 566+ 用例全过（Node.js 207 + Java 330 + 前端 29）| CI/CD 就绪 | **开发：** 单人

---

## 一句话介绍

帮助广应科肇庆校区学生，通过校内 C2C 二手交易平台，实现安全、可信的闲置物品面交换。核心是"手机号实名 + 站内 IM 留证 + 双向评价信誉体系"。

---

## 技术栈

### 前端

| 层 | 选型 | 说明 |
|----|------|------|
| 小程序框架 | uni-app (Vue 3) | Composition API + Pinia，HBuilder X 开发 |
| 样式 | SCSS | 设计令牌集中管理（tokens.scss），8pt 间距系统 |
| 状态管理 | Pinia | 3 个 store（user/chat/app） |
| 测试 | vitest | 29 前端测试用例 |

### Node.js 后端（生产可用）

| 层 | 选型 | 说明 |
|----|------|------|
| 运行时 | Node.js + Express | 5 层结构（routes/controllers/services/repository/models） |
| 数据库 | MySQL 8.0 + mysql2 | 连接池 + 参数化查询，原生 SQL 无 ORM |
| 鉴权 | JWT 双 Token | Access Token（短期）+ Refresh Token（长期） |
| 日志 | winston | 按天切割，3 级 logger |
| 测试 | vitest + supertest | 207 用例，< 30s 全量通过 |
| 部署 | PM2 (fork) + Nginx | 腾讯云轻量服务器 |

### Java 分布式后端（微服务框架开发中，Phase 0~16）

| 层 | 选型 | 说明 |
|----|------|------|
| 框架 | Spring Boot 3.2 + MyBatis | 4 微服务 + 1 共享库（与 Node.js 原生 SQL 风格一致） |
| 微服务网关 | Spring Cloud Gateway | 统一入口（8080），路由转发 + JWT 校验 + Sentinel 流控 + CORS |
| 注册中心 | Nacos 2.5.2 | 服务发现 + 配置中心（Sentinel 规则持久化） |
| 流控熔断 | Sentinel | 接口限流 + Feign 降级 + BlockHandler + Nacos 规则持久化 |
| 分布式事务 | Seata AT 模式 | @GlobalTransactional + undo_log 回滚快照 + 跨服务强一致性 ✅ |
| 分布式锁 | Redisson | RLock 可重入锁 + WatchDog 自动续期（替代基础 SETNX）✅ |
| 缓存 | Redis 7.4 + Lettuce | Cache-Aside 模式（商品列表/用户信息/Token 黑名单） |
| 数据库迁移 | Flyway | 版本化 SQL 迁移，`CREATE TABLE IF NOT EXISTS` 幂等建表 |
| 鉴权 | Spring Security + JWT | OncePerRequestFilter + tokenVersion 校验 + 封禁检查（与 Node.js 行为一致） |
| 内部通信 | OpenFeign | core ↔ admin ↔ im-connector，共享密钥 X-Internal-Token |
| 测试 | JUnit 5 + Mockito | 330 测试用例（43 common + 13 gateway + 201 core + 54 admin + 19 im） |
| 构建 | Maven | 父 POM + 5 子模块 |

**4 微服务架构：**

```
                    ┌─ Nacos (:8848) 服务发现 + 配置中心
                    ├─ Sentinel 流控熔断 + Feign 降级
                    ├─ Seata AT 分布式事务协调
gateway (:8080) ─── ├─ Redisson 分布式锁 (WatchDog)
  统一入口           ├─ Redis (:6379) Cache-Aside 缓存
  JWT 鉴权           │
  CORS + 限流        │
                    ├── core-service (:8081)   ← 商品/订单/用户/评价/举报/搜索
                    ├── admin-service (:8082)  ← 管理后台/敏感词/数据看板/审计日志
                    └── im-connector (:8083)   ← IM UserSig 生成/COS STS
```

### 共享基础设施

| 层 | 选型 | 说明 |
|----|------|------|
| 即时通讯 | 腾讯云 IM SDK | 免费档 100 日活，Provider 抽象层可替换 |
| 文件存储 | 腾讯云 COS | STS 临时密钥，前端直传 |
| 敏感词过滤 | DFA 算法 | 9 类词库 + 管理 API |
| CI/CD | GitHub Actions | Node.js + Java 双流水线 |

---

## 项目结构

```
campus-market/
├── miniprogram/              ← uni-app 小程序（Vue 3）
│   ├── pages/                ← 25 个页面（登录/首页/商品/搜索/聊天/订单/用户/举报/管理/协议/错误/通知/互评/设置/关于）
│   ├── components/           ← 6 个通用组件（ProductCard/FilterSidebar/ImageUploader/StarRating/AppNavbar/EmptyState）
│   ├── api/                  ← 6 个接口封装模块（auth/product/order/review/report/admin）
│   ├── store/                ← 3 个 Pinia store（user/chat/app）
│   └── utils/                ← IM 初始化 + COS 上传 + 下载队列
├── server/                   ← Node.js Express 后端（生产可用）
│   └── src/
│       ├── routes/           ← 10 个路由模块
│       ├── controllers/      ← 10 个控制器
│       ├── services/         ← 12 个服务（含 IM Provider 抽象层）
│       ├── repository/       ← 6 个数据访问层
│       ├── models/           ← mysql2 连接池
│       ├── middleware/        ← 7 个中间件（JWT/权限/校验/限流/错误处理/日志）
│       ├── config/           ← 环境变量集中管理
│       └── utils/            ← 9 个工具模块（COS/微信/IM/错误码/日志/缓存/敏感词/性能）
├── server-java/              ← Java Spring Boot 分布式后端（开发中）
│   ├── common/               ← 共享库（实体类/DTO/枚举/工具类/异常/统一响应）
│   ├── gateway/              ← API 网关 :8080（路由转发 + JWT 校验 + Sentinel + CORS）
│   ├── core-service/         ← 核心业务 :8081（商品/订单/用户/评价/举报/搜索）
│   ├── admin-service/        ← 管理后台 :8082（用户管理/商品下架/数据看板/审计日志/敏感词库）
│   ├── im-connector/         ← IM 连接器 :8083（UserSig 生成/COS STS）
│   ├── scripts/              ← 运维脚本
│   └── memory/               ← Java 后端开发过程记录
├── rules/                    ← 14 份编码规范（命名/函数/文件/API/数据库/安全/错误处理/性能/测试/Git/注释/UI/AI行为/AI输出）
├── docs/                     ← 项目文档（24+ 份，含核心设计/工程/法律/审阅/考核）
├── .github/workflows/        ← CI/CD 流水线
└── .claude/                  ← Claude Code 配置（skills/memory）
```

---

## 快速开始

> 📖 **详细步骤请参阅：[开发环境搭建指南](docs/开发环境搭建指南.md)**（含外部服务开通、常见问题排查、多设备协作等完整说明）

### 环境要求

- **Node.js** ≥ 18.x
- **MySQL** ≥ 8.0
- **JDK** ≥ 17（仅 Java 后端需要）
- **Maven** ≥ 3.8（仅 Java 后端需要）
- **Redis** ≥ 7.0（仅 Java 后端需要）
- **HBuilder X** + **微信开发者工具**
- **腾讯云账号**（COS + IM + 微信小程序）

### Node.js 后端（5 分钟快速启动）

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

### Java 分布式后端（开发中）

```bash
cd server-java
cp .env.example .env                 # 编辑填入 DB/JWT/Redis/微信 等密钥
mysql -u root -p -e "CREATE DATABASE campus_market_dev"
# 启动顺序：Nacos → Redis → 微服务
cd core-service && mvn spring-boot:run    # http://localhost:8081
cd admin-service && mvn spring-boot:run   # http://localhost:8082
cd im-connector && mvn spring-boot:run    # http://localhost:8083
cd gateway && mvn spring-boot:run         # http://localhost:8080（统一入口）
```

> 📖 **双后端说明：** 两套后端共享同一套 API 契约（43 个端点 + 统一响应格式）。前端改一行 `BASE_URL` 即可切换。详见 [双后端切换说明](docs/双后端切换说明.md)。

### 运行测试

```bash
# Node.js 后端
cd server
npx vitest run              # 全量测试 207 用例（< 30s）
npx vitest                  # 监听模式
npx vitest run --coverage   # 覆盖率报告

# Java 后端
cd server-java
mvn test                     # 全量测试 330 用例
mvn -pl common test          # common 模块 43 用例
mvn -pl core-service test    # core-service 模块 201 用例
mvn -pl admin-service test   # admin-service 模块 54 用例
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
| [前端架构文档.md](docs/前端架构文档.md) | 前端完整架构：页面树、组件依赖、Pinia store 设计、API 层封装、IM/COS 集成、性能优化 | 前端开发者 |

### 工程文档

| 文档 | 说明 | 适合谁读 |
|------|------|---------|
| [开发环境搭建指南.md](docs/开发环境搭建指南.md) | 从零搭建开发环境：账号注册、外部服务开通、安装配置、常见问题排查 | **新成员 / 首次搭建** |
| [编码迭代计划.md](docs/编码迭代计划.md) | Node.js 后端 13 轮迭代详细计划：每轮子步骤、验证标准、文件清单 | 开发者 |
| [双后端切换说明.md](docs/双后端切换说明.md) | Node.js ⇄ Java 双后端架构关系、切换方式、对比验证、各阶段使用建议 | 后端开发者 |
| [测试计划.md](docs/测试计划.md) | 134 条测试用例（92 单元 + 29 集成 + 13 定时任务）、错误码覆盖检查表、PRD 可追溯性矩阵 | 开发者 / QA |
| [运维手册.md](docs/运维手册.md) | 12 章 + 2 附录：部署流程（含迁移+回滚）、PM2/Nginx 配置、定时任务、备份恢复（GFS 模型）、监控告警、故障处理 SOP（6 场景） | 运维 |
| [benchmark-results.md](docs/benchmark-results.md) | 压力测试基线结果（wrk 多场景） | 运维 / 开发者 |

### 微信审核材料

| 文档 | 说明 | 适合谁读 |
|------|------|---------|
| [wechat-review-notes.md](docs/wechat-review-notes.md) | 微信小程序审核备注：类目选择、资质要求、审核要点 | 项目管理者 |
| [wechat-review-account.md](docs/wechat-review-account.md) | 审核测试账号：供微信审核团队登录验证使用 | 审核团队 |

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

## 开发路线

### Node.js 后端（13 轮迭代，全部完成 ✅）

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
| 13 | 测试 + CI + 部署 | 55 后端测试 + GitHub Actions CI + nginx + 压力测试基线 + Ship-Gate 89 项审计 + 审核材料 | ✅ |

### Java 分布式后端（17 Phase，核心能力 0~16，Phase 17 待开始）

| Phase | 内容 | 核心交付 | 状态 |
|:--:|------|------|:--:|
| 0 | 环境验证 | JDK 17 + Maven + MySQL 连接 + Nacos 安装 | ✅ |
| 1 | 项目骨架 | 父 POM + 5 子模块 + common 6 文件（ErrorCode/BusinessException/ResponseBuilder/PageResult）| ✅ |
| 2 | 数据库层 | 14 实体类 + MyBatis Mapper + Repository + Flyway 幂等建表 | ✅ |
| 3 | 核心服务 | 登录/注册/商品 CRUD/搜索 — Controller + Service + Repository 全链路 + JWT 鉴权 | ✅ |
| 4 | 订单+评价 | 订单状态机（pending→met→completed/cancelled）+ FOR UPDATE 悲观锁 + 双向互评 | ✅ |
| 5 | 管理后台 | 用户管理 + 商品下架 + 数据看板 + 审计日志 + 敏感词库管理 | ✅ |
| 6 | IM 连接器 | UserSig 生成 + COS STS 临时密钥 + 内部 API 共享密钥鉴权 | ✅ |
| 7 | API 网关 | Gateway 统一入口 (:8080) + 路由转发 + JWT 校验 + CORS + 白名单路径 | ✅ |
| 8 | 中间件+契约验证 | 全局异常处理 + 参数校验 + Feign 内部调用 + Node.js vs Java API 契约对比（14 APIs, 71%）| ✅ |
| 9 | Sentinel 流控熔断 | 接口限流 + Feign Fallback 降级 + Gateway 流控规则 + BlockHandler + Nacos 持久化 | ✅ |
| 10 | Redis 缓存 | 商品列表/用户信息缓存（Cache-Aside）+ Token 黑名单 + 分布式锁 + Sentinel 规则缓存 | ✅ |
| 11 | 测试+回归 | 144 tests PASS + 14 APIs 契约对比 (71%) + 前端 build 成功 + P0 修复 6 项 | ✅ |
| 12 | 部署+文档 | Docker 容器化 + 生产配置 + 运维 SOP | ❌ |
| **13** | **分布式事务（Seata）** | **2 个 @GlobalTransactional（confirmOrder/resolveTicket）+ 4 Feign 分支 + undo_log 回滚快照表 + Seata AT 模式跨服务强一致性 + 2 个内部端点（applyPenalty/createLog）+ InternalAuth 共享密钥鉴权 + 301 tests PASS** | **✅** |
| 14 | 消息驱动 | RocketMQ 异步通知 + 重试 + 死信队列（替代同步 Feign 推送）| ❌ |
| 15 | 全链路追踪 | Micrometer Tracing + Zipkin + traceId 跨 4 服务日志串联 | ❌ |
| **16** | **分布式锁增强（Redisson）** | **Redisson RLock 可重入锁 + WatchDog 自动续期 + SETNX→RLock 替换 + 34 tests PASS + 8 文件改动** | **✅** |
| 17 | 限流补全 | Sentinel 热点参数限流 + 授权规则 + 系统自适应限流 | ❌ |

---

## 前置设计完成清单

- [x] PRD（产品需求文档）
- [x] 技术架构文档（23 章，含 DB/API/IM/部署/测试/性能基准）
- [x] UI 设计系统文档（设计令牌 + 组件规范 + 页面布局）
- [x] 前端架构文档（页面树 + 组件依赖 + Store/Api 层设计）
- [x] 法律合规文件（用户协议 + 隐私政策 + 免责声明）
- [x] 环境变量规范（25+ 变量，3 环境对比）
- [x] IM 消息协议（4 种系统消息 JSON Schema）
- [x] 敏感词库规范（DFA 算法 + 9 类词库 + 管理 API）
- [x] 14 份编码规范（rules/ — 命名/函数/文件/API/数据库/安全/错误处理/性能/测试/Git/注释/UI/AI行为/AI输出）
- [x] **Node.js 后端代码开发（第 1~13 轮全部完成）** ← v1.0 编码完结
- [x] **Java 分布式后端 Phase 0~16（0~11 核心 + 13 Seata 分布式事务 + 16 Redisson 分布式锁 ✅，12/14/15/17 待开始）**
- [x] **测试 566+ 用例全绿（Node.js 207 + Java 330 + 前端 29）**
- [x] **CI/CD 流水线就绪（GitHub Actions Node.js + Java 双流水线）**
- [x] **Ship-Gate 89 项审计通过（⚠️ Go with Warnings）**
- [x] 前端大文件拆分（3 文件超 500 行 → 全部 ≤500 行，提取 8 组件 + 2 composable + 1 工具函数）
- [x] 微信审核材料准备（审核测试账号 + 审核备注）
- [x] **Seata AT 模式分布式事务**（2 个 @GlobalTransactional + undo_log + 4 Feign 分支 + InternalAuth 内部鉴权 + 12 项审查全部修复）
- [x] **Redisson 分布式锁增强**（RLock 可重入锁 + WatchDog 自动续期 + 34 tests PASS）
- [ ] RocketMQ 消息驱动（Phase 14）
- [ ] Zipkin 全链路追踪（Phase 15）
- [ ] Java 后端 Docker 容器化部署（Phase 12）
- [ ] 生产 Linux 服务器部署验证
- [ ] 全链路真机烟雾测试
- [ ] 微信小程序提交审核

---

## 许可证

本项目为个人学习与校园服务项目，暂未选定开源许可证。
