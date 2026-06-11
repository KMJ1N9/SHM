# 第 13 轮编码计划：测试 + CI + 部署

> **状态：** 🔲 待开始
> **预估工时：** ~10 h ≈ 2 天
> **目标：** 测试缺口补全 → GitHub Actions CI 流水线 → 部署验证 → 压力测试基线 → Ship-Gate 审计 → 微信审核材料准备
> **依据文档：** 编码迭代计划 §第 13 轮、技术架构文档 §九（工程化基础设施）、API 接口文档、测试计划、运维手册、rules/ 全部 14 份规范

---

## 〇、本轮特殊性

编码迭代计划原文（§第 13 轮 L1687~L1858）：

> **目标：90+ 测试用例通过 + GitHub Actions CI + PM2 部署验证**
> 原计划：后端测试 103 用例 + 前端 CI 配置 + PM2 + 压力测试

**2026-06-11 现状评估：** 后端已有 **143 测试用例（10 文件）** 全部通过，前端已有 **29 测试用例（2 文件）** 全部通过，合计 **172 测试用例**。原计划「103 条测试」目标已在前期轮次（第 3T/4/5/6/11/12 轮）超额完成。

本轮实际工作重校准为 **6 Phase**：

| Phase | 内容 | 性质 | 优先级 |
|:--:|------|:--:|:--:|
| 1 | 后端测试缺口补全 | 补漏 — 未覆盖的 service/middleware/integration | 🟡 P1 |
| 2 | GitHub Actions CI 流水线 | 新建 — 自动化测试+构建 | 🟡 P1 |
| 3 | PM2 部署验证 + 运维文档 | 验证 — 配置已就绪，需实测+文档 | 🟡 P1 |
| 4 | 压力测试基线 | 新建 — ab 压测 + 结果记录 | 🟢 P2 |
| 5 | Ship-Gate 最终审计 | 审计 — 89 项检查清单 | 🔴 P0 |
| 6 | 微信审核材料准备 | 文档 — 小程序上架审核 | 🔴 P0 |

---

## 一、现状分析

### 1.1 测试现状总览

**后端（server/）：**

| 文件 | 类型 | 用例数 | 覆盖模块 | 完成于 |
|------|:--:|:--:|------|:--:|
| `__tests__/unit/services/product.test.js` | 单元 | 42 | product service（含 8 缓存用例） | 第 3T+11 轮 |
| `__tests__/unit/services/order.test.js` | 单元 | 12 | order service | 第 6 轮 |
| `__tests__/unit/services/review.test.js` | 单元 | 8 | review service | 第 6 轮 |
| `__tests__/unit/services/report.test.js` | 单元 | 5 | report service | 第 7 轮 |
| `__tests__/unit/services/search.test.js` | 单元 | 13 | search (product.list keyword) | 第 4 轮 |
| `__tests__/unit/utils/sensitive-filter.test.js` | 单元 | 18 | DFA 敏感词过滤 | 第 3T 轮 |
| `__tests__/unit/utils/cos.test.js` | 单元 | 8 | COS STS 凭证 | 第 3T 轮 |
| `__tests__/integration/products.test.js` | 集成 | 19 | 商品 CRUD 全流程 | 第 3T 轮 |
| `__tests__/integration/search.test.js` | 集成 | 9 | 搜索 API | 第 4 轮 |
| `__tests__/integration/im.test.js` | 集成 | 8 | IM UserSig 签发 | 第 5 轮 |
| **合计** | | **143** | | |

**前端（miniprogram/）：**

| 文件 | 类型 | 用例数 | 覆盖模块 | 完成于 |
|------|:--:|:--:|------|:--:|
| `__tests__/utils/im.test.js` | 单元 | 9 | IM 工具函数 | 第 12 轮 |
| `__tests__/store/user.test.js` | 单元 | 20 | Pinia user store | 第 12 轮 |
| **合计** | | **29** | | |

**全部：172 用例 / 12 文件 / 0 failures**

### 1.2 测试缺口识别

以下模块在编码迭代计划 §13.1 中列明但尚未覆盖：

#### 缺口 A：后端 service 层（3 模块未覆盖）🟡

| 模块 | 文件 | 行数 | 核心方法 | 原计划用例 |
|------|------|:--:|------|:--:|
| `services/auth.js` | 4 个方法 | ~150 | login / refreshToken / getMe / logout | 7 |
| `services/credit.js` | 3 个方法 | ~80 | getMyCredit / getUserPublicCredit / changeScore | 5 |
| `services/notification.js` | 5 个方法 | ~100 | list / unreadCount / markAsRead / markAllAsRead / create | 5 |

#### 缺口 B：后端 middleware 层（3 模块未覆盖）🟡

| 模块 | 文件 | 核心逻辑 | 原计划用例 |
|------|------|------|:--:|
| `middleware/auth.js` | JWT 验证 + token_version + 封禁检查 + 白名单 | 6 |
| `middleware/rate-limiter.js` | 令牌桶限流 + 全局限流 + 敏感接口限流 | 4 |
| `middleware/validate.js` | Joi schema 校验中间件 | 4 |

#### 缺口 C：集成测试（5 端点未覆盖）🟡

| 端点组 | 核心场景 | 原计划用例 |
|------|------|:--:|
| auth 集成 | 登录→Token→refresh→me | 5 |
| orders 集成 | 下单→面交→确认→取消（状态机全路径） | 8 |
| reviews 集成 | 评价创建+重复拦截+状态校验 | 4 |
| reports 集成 | 举报创建+重复拦截+列表/详情 | 4 |
| rate-limit 集成 | 全局限流+敏感接口限流 | 2 |

#### 缺口 D：前端测试（可扩展但不强制）🟢

| 候选 | 覆盖内容 | 优先级 |
|------|------|:--:|
| api 模块 | 请求参数 snake_case 正确性 | P2 |
| StarRating 组件 | modelValue / readonly / 触摸事件 | P2 |
| ImageUploader 组件 | 格式校验 / 大小校验 / 并发控制 | P2 |

### 1.3 CI/CD 现状 🔴

```
$ ls .github/workflows/
# 目录不存在 — 零 CI 配置
```

| 检查项 | 状态 |
|------|:--:|
| GitHub Actions workflow | ❌ 不存在 |
| 自动化测试（push/PR 触发） | ❌ 不存在 |
| Lint 门禁 | ❌ 不存在 |
| Build 验证 | ❌ 不存在 |
| 测试覆盖率报告 | ❌ 不存在 |

### 1.4 部署配置现状 🟡

| 文件 | 状态 | 完成度 |
|------|:--:|:--:|
| `server/ecosystem.config.js` | ✅ 已存在 | fork 模式/单实例/256M 内存/日志轮转/优雅退出 |
| `server/.env.example` | ✅ 已存在 | 25+ 环境变量模板 |
| `server/.env.development` | ✅ 已存在 | 开发环境配置 |
| `server/.env.test` | ✅ 已存在 | 测试环境配置 |
| `server/.env.production` | ⚠️ 待确认 | 生产环境是否配置 |
| `docs/运维手册.md` | ✅ 已存在 | 部署/备份/监控/故障处理 |
| nginx 配置 | ❌ 不存在 | 反向代理 + HTTPS + 静态资源缓存 |

### 1.5 压力测试现状 🔴

未执行过任何压力测试。编码迭代计划 §13.5 定义了 3 组基线测试：

| 场景 | 命令 | 目标 |
|------|------|------|
| 商品列表（高频） | `ab -n 1000 -c 50` | P50 < 80ms |
| 商品详情（主键） | `ab -n 2000 -c 100` | P50 < 50ms |
| 健康检查 | `ab -n 5000 -c 200` | P50 < 10ms |

### 1.6 微信审核材料现状 🔴

| 材料 | 状态 | 说明 |
|------|:--:|------|
| 小程序名称/简介 | ❌ | 需准备 4-30 字简介 |
| 类目选择 | ❌ | 二手交易 → 对应类目资质 |
| 功能截图（5 张） | ❌ | 首页/商品详情/聊天/订单/个人中心 |
| 隐私政策 + 用户协议 | ✅ | 已在第 12 轮完成内容页 |
| 测试账号 | ❌ | 提供给微信审核人员的测试账号 |
| 审核说明文档 | ❌ | 小程序流程说明+敏感词过滤机制描述 |

---

## 二、架构关键决策

### 决策 1：测试策略 — 聚焦缺口，不堆数量

当前 172 用例已覆盖核心闭环（商品→搜索→订单→评价→举报→IM）。本轮补漏策略：

- **service 层**：auth / credit / notification — 核心但测试缺失，必须补齐
- **middleware 层**：auth / rate-limiter — 安全关键组件，必须补齐
- **集成测试**：auth + orders 全路径 — 端到端验证 JWT 状态机
- **不做**：前端组件测试（StarRating/ImageUploader）、纯工具函数测试（perf.js/logger.js/cache.js）— 低 ROI，不阻塞发版

### 决策 2：CI 策略 — GitHub Actions + MySQL Service Container

```
触发条件：push → main/master 分支 + pull_request
Job 矩阵：单 job（test），包含 lint + test + build
MySQL：GitHub Actions service container（mysql:8.0）
覆盖率：上传到 Codecov / 或仅 console 输出
```

### 决策 3：部署策略 — PM2 fork 模式（已确认）

与 ecosystem.config.js 已有配置一致：fork 单实例（node-cron 防重复）+ `max_memory_restart: 256M`。本轮仅需验证 `pm2 start` → `status online` → `curl /api/health`。

### 决策 4：微信审核 — 提前准备但不提前提交

审核材料本轮全部产出，但实际提交由用户人工操作（微信公众平台后台需要人工扫码 + 填写表单）。产出物为：5 张功能截图 + 测试账号 + 审核说明 Markdown 文档。

---

## 三、Phase 1：后端测试缺口补全（6 文件，~70 用例）

**目标：** 覆盖 3 个遗漏 service + 3 个 middleware + 3 组集成测试

**预估工时：** 3 h

### Phase 1-1：auth service 单元测试

**文件：`server/__tests__/unit/services/auth.test.js`**（新建）

```js
// 测试覆盖（~10 用例）：
// AU-001: mock 登录成功 → 返回双 Token + 用户信息
// AU-002: 已存在用户登录 → is_new_user = false
// AU-003: 新用户自动注册 → is_new_user = true + 默认昵称
// AU-004: 封禁用户登录 → 返回 1004（USER_BANNED）
// AU-005: refresh token 成功 → 返回新 access_token
// AU-006: refresh token 过期/无效 → 返回 1002（TOKEN_INVALID）
// AU-007: refresh token 版本不匹配 → 返回 1002
// AU-008: getMe 查询 → 返回当前用户信息
// AU-009: getMe 用户不存在 → 返回 1003
// AU-010: 微信 code 为空 → 返回 4001（VALIDATION_ERROR）
```

**关键 mock 策略：**
- `axios.post` mock 微信 `jscode2session` 返回 → 手机号
- `repository/user.js` mock：`findByPhone` / `findById` / `create` / `updateTokenVersion`
- 不启动 Express 服务器，纯 service 层单元测试

### Phase 1-2：credit service 单元测试

**文件：`server/__tests__/unit/services/credit.test.js`**（新建）

```js
// 测试覆盖（~6 用例）：
// CR-001: getMyCredit → 返回用户信誉分 + 变动记录分页
// CR-002: getUserPublicCredit → 返回公开信息（分数+评价汇总，无敏感字段）
// CR-003: changeScore 加分 → credit_score 增加 + change_logs 有记录
// CR-004: changeScore 扣分 → credit_score 减少但不低于 0
// CR-005: changeScore 无效分数值 → 返回错误
// CR-006: 分页参数容错 — page/pageSize 字符串自动 parseInt
```

### Phase 1-3：notification service 单元测试

**文件：`server/__tests__/unit/services/notification.test.js`**（新建）

```js
// 测试覆盖（~6 用例）：
// NF-001: list → 返回当前用户通知（按时间倒序）
// NF-002: list 类型筛选 → 仅返回指定类型
// NF-003: unreadCount → 返回未读数量
// NF-004: markAsRead → 更新 is_read = 1
// NF-005: markAllAsRead → 批量更新当前用户全部未读
// NF-006: create → 创建通知记录
```

### Phase 1-4：middleware/auth.js 单元测试

**文件：`server/__tests__/unit/middleware/auth.test.js`**（新建）

```js
// 测试覆盖（~8 用例）：
// MW-AU-001: 有效 JWT → next() 被调用，req.user 已挂载
// MW-AU-002: 无 Authorization header → 1001 UNAUTHENTICATED
// MW-AU-003: Bearer token 格式错误 → 1001
// MW-AU-004: JWT 签名无效 → 1001
// MW-AU-005: token_version 不匹配 → 1002 TOKEN_INVALID（用户被封禁后 token 失效）
// MW-AU-006: 用户已封禁 → 1004 USER_BANNED
// MW-AU-007: 白名单路径豁免 → 无需 token 直接 next()
// MW-AU-008: 用户不存在 → 1003 USER_NOT_FOUND
```

**关键 mock 策略：**
- 使用 `jsonwebtoken.sign()` 生成真实 JWT（测试环境密钥）
- `repository/user.js.findById` mock 返回不同状态的 user 对象
- 模拟 Express `req`/`res`/`next` 对象

### Phase 1-5：middleware/rate-limiter.js 单元测试

**文件：`server/__tests__/unit/middleware/rate-limiter.test.js`**（新建）

```js
// 测试覆盖（~5 用例）：
// RL-001: 正常请求 → next() 被调用，X-RateLimit 头已设置
// RL-002: 全局超限（1 分钟内 >60 次）→ 6020 RATE_LIMIT_EXCEEDED
// RL-003: 敏感接口超限（1 分钟内 >10 次）→ 6020
// RL-004: 限流窗口重置 → 下一分钟恢复
// RL-005: 不同 IP 独立计数
```

### Phase 1-6：middleware/validate.js 单元测试

**文件：`server/__tests__/unit/middleware/validate.test.js`**（新建）

```js
// 测试覆盖（~5 用例）：
// VL-001: 有效参数 → next() 被调用
// VL-002: 缺少必填字段 → 4001 VALIDATION_ERROR（含字段名）
// VL-003: 字段类型错误（string 传 number）→ 4001
// VL-004: 字段值超出范围（价格 < 0）→ 4001
// VL-005: 未知字段 → 被 Joi.stripUnknown 移除，不报错
```

### Phase 1-7：集成测试补充

#### 1-7a：auth 集成测试

**文件：`server/__tests__/integration/auth.test.js`**（新建）

```js
// 测试覆盖（~7 用例）：
// AI-001: 完整登录流程 → POST /api/auth/login → 返回 access_token + refresh_token + user
// AI-002: 携带有效 token 访问受保护端点 → 200
// AI-003: 无 token 访问受保护端点 → 401（1001）
// AI-004: refresh token 续期 → POST /api/auth/refresh → 新 access_token
// AI-005: 过期 token 访问 → 401
// AI-006: GET /api/auth/me → 返回当前用户信息
// AI-007: logout → 成功
```

**注意：** auth 集成测试依赖微信 mock 模式（`.env.test` 中 `WX_MOCK_MODE=true`），使用 mock 手机号登录。现有 `setup.js` 的 `createTestUser()` + `authHeader()` 已支持此模式。

#### 1-7b：orders 集成测试

**文件：`server/__tests__/integration/orders.test.js`**（新建）

```js
// 测试覆盖（~10 用例）：
// OI-001: 创建订单成功 → POST /api/orders → 201 + order
// OI-002: 幂等防重 → 同一 buyer+product 重复下单被拒
// OI-003: 不能购买自己的商品 → 返回业务错误
// OI-004: 商品已锁定 → 返回商品不可用错误
// OI-005: 信誉分不足 → 低于 30 分无法下单
// OI-006: 获取订单列表 → GET /api/orders → 分页结果
// OI-007: 获取订单详情 → GET /api/orders/:id → 含买卖双方信息
// OI-008: 标记已面交 → PUT /api/orders/:id/met → 200
// OI-009: 确认收货 → PUT /api/orders/:id/confirm → 200 + 商品状态变为 sold
// OI-010: 取消订单 → PUT /api/orders/:id/cancel → 200 + 商品恢复 active
```

**注意：** 每个用例需要 2 个测试用户（buyer + seller）和 1 个测试商品。`setup.js` 已有 `createTestUser()` 工厂函数，可复用。需新建 `createTestProduct()` 辅助函数。

---

## 四、Phase 2：GitHub Actions CI 流水线

**目标：** push/PR 自动触发 → lint → test → build 全链路

**预估工时：** 1.5 h

### Phase 2-1：CI Workflow 文件

**文件：`.github/workflows/ci.yml`**（新建）

```yaml
name: CI

on:
  push:
    branches: [master, main]
  pull_request:
    branches: [master, main]

jobs:
  backend-test:
    runs-on: ubuntu-latest
    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: test_password
          MYSQL_DATABASE: campus_market_test
        ports:
          - 3306:3306
        options: >-
          --health-cmd="mysqladmin ping -h localhost"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=5

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '18'
          cache: 'npm'
          cache-dependency-path: server/package.json

      - name: Install server dependencies
        run: cd server && npm ci

      - name: Copy test env
        run: |
          cp server/.env.example server/.env.test
          # 覆盖测试环境变量
          echo "DB_HOST=127.0.0.1" >> server/.env.test
          echo "DB_PORT=3306" >> server/.env.test
          echo "DB_USER=root" >> server/.env.test
          echo "DB_PASSWORD=test_password" >> server/.env.test
          echo "DB_NAME=campus_market_test" >> server/.env.test
          echo "JWT_ACCESS_SECRET=ci-test-access-secret" >> server/.env.test
          echo "JWT_REFRESH_SECRET=ci-test-refresh-secret" >> server/.env.test
          echo "WX_MOCK_MODE=true" >> server/.env.test
          echo "IM_MOCK_MODE=true" >> server/.env.test
          echo "COS_MOCK_MODE=true" >> server/.env.test

      - name: Run backend tests
        run: cd server && npx vitest run
        timeout-minutes: 5

  frontend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '18'
          cache: 'npm'
          cache-dependency-path: miniprogram/package.json

      - name: Install miniprogram dependencies
        run: cd miniprogram && npm ci

      - name: Run frontend tests
        run: cd miniprogram && npx vitest run
        timeout-minutes: 2

  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '18'

      - name: Install server dependencies
        run: cd server && npm ci

      - name: Server ESLint
        run: cd server && npx eslint src/ --ext .js || true
        # 初期不阻塞 CI，稳定后移除 || true

      - name: Install miniprogram dependencies
        run: cd miniprogram && npm ci

      - name: Miniprogram ESLint
        run: cd miniprogram && npx eslint src/ --ext .js,.vue || true

  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '18'
          cache: 'npm'
          cache-dependency-path: miniprogram/package.json

      - name: Install miniprogram dependencies
        run: cd miniprogram && npm ci

      - name: Build miniprogram
        run: cd miniprogram && npm run build:mp-weixin
        timeout-minutes: 5
```

### Phase 2-2：Badge + README

在 `README.md` 顶部添加 CI badge（workflow 创建后）：

```markdown
[![CI](https://github.com/<user>/<repo>/actions/workflows/ci.yml/badge.svg)](https://github.com/<user>/<repo>/actions/workflows/ci.yml)
```

### Phase 2-3：CI 验证

| # | 验证项 | 方法 |
|:--:|------|------|
| 1 | push 触发 CI 流水线 | 推送 commit → GitHub Actions 自动运行 |
| 2 | 4 个 Job 全部通过 | backend-test ✅ / frontend-test ✅ / lint ✅ / build ✅ |
| 3 | PR 触发 CI 流水线 | 创建 PR → Actions 运行 |
| 4 | MySQL service container 正常 | backend-test 中测试不与数据库连接失败 |

---

## 五、Phase 3：PM2 部署验证 + 运维文档

**目标：** 验证 PM2 配置可启动 + 补充 nginx 配置模板 + 更新运维手册

**预估工时：** 1.5 h

### Phase 3-1：PM2 配置审查

现有 `server/ecosystem.config.js`（56 行）已包含：
- fork 模式 / 单实例
- 日志轮转（10M / 保留 7 天）
- 自动重启（最多 10 次 / 5s 延迟）
- 256M 内存限制
- 优雅退出（kill_timeout 10s / wait_ready）
- `listen_timeout` 10s

**审查要点：**

| 检查项 | 状态 | 说明 |
|------|:--:|------|
| `name` 正确 | ✅ | `campus-market` |
| `script` 路径正确 | ✅ | `src/app.js` |
| fork 模式（非 cluster） | ✅ | node-cron 防重复 |
| 环境变量 | ⚠️ | 需确认 `.env.production` 存在 |
| 日志路径 | ✅ | `./logs/pm2-*.log` |
| 开机自启 | ⚠️ | `pm2 save && pm2 startup` 需手动执行 |

### Phase 3-2：nginx 配置模板

**文件：`server/nginx.conf`**（新建）

```nginx
# 校园二手交易小程序 — nginx 反向代理配置
# 适用场景：Express 监听 127.0.0.1:3000，nginx 对外暴露 80/443

server {
    listen 80;
    server_name api.example.com;

    # 静态资源（COS 直传后 Redirect 回服务端）
    location /images/ {
        alias /path/to/server/public/images/;
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 超时设置
        proxy_connect_timeout 10s;
        proxy_read_timeout 30s;
        proxy_send_timeout 30s;
    }

    # 健康检查（不记录日志）
    location = /api/health {
        proxy_pass http://127.0.0.1:3000;
        access_log off;
    }
}
```

### Phase 3-3：部署验证清单

```bash
# 1. 确认 .env.production 存在且配置正确
cat server/.env.production

# 2. 安装依赖
cd server && npm ci --production

# 3. 启动 PM2
pm2 start ecosystem.config.js

# 4. 验证状态
pm2 status                    # campus-market: online

# 5. 验证健康检查
curl http://localhost:3000/api/health
# → {"code":0,"message":"success","data":{"status":"healthy"}}

# 6. 验证日志输出
pm2 logs campus-market --lines 20

# 7. 验证重启策略
pm2 restart campus-market     # 应在 5s 内恢复

# 8. 停止
pm2 stop campus-market
```

### Phase 3-4：运维手册更新

在 `docs/运维手册.md` 中补充/更新：
- PM2 启动/停止/重载指令（与 ecosystem.config.js 对齐）
- nginx 配置说明（引用 nginx.conf）
- 数据库备份脚本确认
- 健康检查端点说明

---

## 六、Phase 4：压力测试基线

**目标：** 3 组 ab 压测 + P50/P95/P99 记录 + 基准文档

**预估工时：** 1 h

### Phase 4-1：压测脚本

**文件：`server/scripts/benchmark.sh`**（新建）

```bash
#!/bin/bash
# 校园二手交易 — 压力测试基线脚本
# 前提：服务已在 localhost:3000 运行

BASE_URL="http://localhost:3000"

echo "=== 1. 健康检查 (5000 req / 200 concurrency) ==="
ab -n 5000 -c 200 "$BASE_URL/api/health" 2>&1 | tee /tmp/bench-health.txt

echo "=== 2. 商品列表（高频）(1000 req / 50 concurrency) ==="
ab -n 1000 -c 50 "$BASE_URL/api/products?page=1&pageSize=20" 2>&1 | tee /tmp/bench-products-list.txt

echo "=== 3. 商品详情（主键查询）(2000 req / 100 concurrency) ==="
ab -n 2000 -c 100 "$BASE_URL/api/products/1" 2>&1 | tee /tmp/bench-product-detail.txt

echo "=== 4. 搜索（FULLTEXT）(500 req / 30 concurrency) ==="
ab -n 500 -c 30 "$BASE_URL/api/products?keyword=教材&page=1&pageSize=20" 2>&1 | tee /tmp/bench-search.txt

echo "Done. Results in /tmp/bench-*.txt"
```

### Phase 4-2：基准结果模板

**文件：`docs/benchmark-results.md`**（新建）

```markdown
# 压力测试基准

**测试环境：** [CPU] / [RAM] / Node.js 18 / MySQL 8.0
**测试日期：** 2026-06-XX

## 健康检查

| 指标 | 值 | P50 | P95 | P99 |
|------|:--:|:--:|:--:|:--:|
| 请求数 | 5000 | | | |
| 并发 | 200 | | | |
| RPS | — | — ms | — ms | — ms |
| 失败 | 0 | | | |

## 商品列表

| 指标 | 值 | P50 | P95 | P99 |
|------|:--:|:--:|:--:|:--:|
| 请求数 | 1000 | | | |
| 并发 | 50 | | | |
| RPS | — | — ms | — ms | — ms |
| 失败 | 0 | | | |

## 商品详情

| 指标 | 值 | P50 | P95 | P99 |
|------|:--:|:--:|:--:|:--:|
| 请求数 | 2000 | | | |
| 并发 | 100 | | | |
| RPS | — | — ms | — ms | — ms |
| 失败 | 0 | | | |

## SLA 达标情况

| 场景 | 目标 P50 | 实际 P50 | 目标 P95 | 实际 P95 | 达标 |
|------|:--:|:--:|:--:|:--:|:--:|
| 健康检查 | < 10ms | — | < 50ms | — | — |
| 商品列表 | < 80ms | — | < 500ms | — | — |
| 商品详情 | < 50ms | — | < 200ms | — | — |
```

---

## 七、Phase 5：Ship-Gate 最终审计

**目标：** 按 ship-gate skill 的 89 项检查清单逐项审计

**预估工时：** 2 h

### Phase 5-1：审计维度

| 维度 | 检查项数 | 关键检查点 |
|------|:--:|------|
| 安全 | ~15 | JWT 密钥不硬编码 / SQL 注入防护 / XSS 防护 / 敏感数据加密 / 生产环境 detail 不泄露 / 限流 |
| 数据库 | ~10 | 无 SELECT * / 参数化查询 / 索引覆盖 / 迁移可回滚 / 无 N+1 / 连接池配置 |
| 部署 | ~8 | .env.example 完整 / PM2 配置 / 优雅退出 / 健康检查 / CORS 配置 |
| 代码质量 | ~15 | 无空 catch / 无 console.log / AppError 一致性 / 函数 ≤80 行 / 文件 ≤500 行 |
| 前端 | ~12 | 无内联样式泛滥 / 无弹窗套弹窗 / tokens.scss 使用 / 移动端适配 / 空状态覆盖 |
| 依赖 | ~8 | 无高危 CVE / 许可证合规 / 无过期 major 版本 |
| 测试 | ~10 | 核心路径有测试 / 覆盖率 ≥ 70% / 测试数据库隔离 |
| 文档 | ~8 | API 文档 vs 实现一致 / README 可跑通 / 变更记录完整 |
| 合规 | ~3 | 用户协议可查看 / 隐私政策可查看 / 注销功能 |

### Phase 5-2：审计执行方式

使用 `ship-gate` skill（claude-skills-main/engineering/ship-gate）的 89 项清单，逐项在本项目中 grep / 审查 / 记录结果。对于不适用项（如 Docker/K8s 相关），标记为 N/A。

### Phase 5-3：审计产出

**文件：`memory/ship-gate-audit.md`**（新建）

记录：
- 通过项数 / 不通过项数 / N/A 项数
- 不通过项的修复计划
- 综合发版建议（Go/No-Go/Warning）

### Phase 5-4：扫描清单（预置）

```bash
# 安全检查
grep -r "console\.log" server/src/ --include="*.js" | grep -v "logger\." | grep -v "migrate"
grep -r "throw new Error" server/src/ --include="*.js" | grep -v "AppError"
grep -rn "SELECT \*" server/src/ --include="*.js"
grep -r "TODO\|FIXME\|HACK" server/src/ miniprogram/src/ --include="*.js" --include="*.vue"

# 代码规范
find server/src/ -name "*.js" -exec wc -l {} \; | sort -rn | head -20
find miniprogram/src/ -name "*.vue" -o -name "*.js" | xargs wc -l | sort -rn | head -20

# 依赖检查
cd server && npm audit --production
cd miniprogram && npm audit --production
```

---

## 八、Phase 6：微信审核材料准备

**目标：** 产出审核所需全部文件 + 截图 + 说明文档

**预估工时：** 1 h

### Phase 6-1：功能截图（5 张）

从微信开发者工具或真机截取：

| 序号 | 页面 | 要求 |
|:--:|------|------|
| 1 | 首页瀑布流 | 含商品卡片 + 筛选侧边栏已展开 |
| 2 | 商品详情 | 含图片轮播 + 卖家信息 + "聊一聊"/"我想要"按钮 |
| 3 | 聊天页面 | 含消息气泡 + 安全提示 Banner |
| 4 | 订单详情 | 含时间线 + 商品快照 + 评价弹窗 |
| 5 | 个人中心 | 含信誉分 + 6 管理入口（admin 视角） |

### Phase 6-2：测试账号

**文件：`docs/wechat-review-account.md`**（新建）

```markdown
# 微信审核测试账号

## 测试账号 1（普通用户）
- 手机号：13800138001
- 角色：普通用户
- 信誉分：100
- 可测试功能：浏览/搜索/发布/聊天/下单/评价/举报

## 测试账号 2（管理员，可选）
- 手机号：13800138002
- 角色：admin
- 可测试功能：用户管理/商品管理/工单处理/数据看板/敏感词管理

## 测试流程建议
1. 使用账号 1 登录 → 浏览首页 → 搜索"教材"
2. 点击商品 → 查看详情 → 点击"我想要"下单
3. 切换到"消息"Tab → 查看系统通知
4. 切换到"我的"Tab → 查看信誉分+评价记录
5. 返回首页 → 点击发布按钮 → 填写商品信息 → 发布
```

### Phase 6-3：审核说明文档

**文件：`docs/wechat-review-notes.md`**（新建）

内容要点：
1. 小程序功能概述（200 字以内）
2. 类目说明（二手交易 → 对应资质）
3. 敏感词过滤机制描述（DFA + 437 词库）
4. 内容审核机制（用户举报 → 客服受理 → 处理通知）
5. 用户隐私保护说明（手机号仅用于登录 + 敏感信息脱敏展示）
6. 用户协议 + 隐私政策页路径

---

## 九、文件清单

### 新建文件（16 个）

| # | 文件 | Phase | 说明 |
|:--:|------|:--:|------|
| 1 | `server/__tests__/unit/services/auth.test.js` | 1-1 | auth service 单元测试（~10 用例） |
| 2 | `server/__tests__/unit/services/credit.test.js` | 1-2 | credit service 单元测试（~6 用例） |
| 3 | `server/__tests__/unit/services/notification.test.js` | 1-3 | notification service 单元测试（~6 用例） |
| 4 | `server/__tests__/unit/middleware/auth.test.js` | 1-4 | JWT 中间件单元测试（~8 用例） |
| 5 | `server/__tests__/unit/middleware/rate-limiter.test.js` | 1-5 | 限流中间件单元测试（~5 用例） |
| 6 | `server/__tests__/unit/middleware/validate.test.js` | 1-6 | 校验中间件单元测试（~5 用例） |
| 7 | `server/__tests__/integration/auth.test.js` | 1-7a | auth 集成测试（~7 用例） |
| 8 | `server/__tests__/integration/orders.test.js` | 1-7b | orders 集成测试（~10 用例） |
| 9 | `.github/workflows/ci.yml` | 2-1 | GitHub Actions CI 配置 |
| 10 | `server/nginx.conf` | 3-2 | nginx 反向代理配置模板 |
| 11 | `server/scripts/benchmark.sh` | 4-1 | 压力测试脚本 |
| 12 | `docs/benchmark-results.md` | 4-2 | 压力测试结果文档 |
| 13 | `memory/ship-gate-audit.md` | 5-3 | Ship-Gate 审计报告 |
| 14 | `docs/wechat-review-account.md` | 6-2 | 审核测试账号 |
| 15 | `docs/wechat-review-notes.md` | 6-3 | 审核说明文档 |
| 16 | `plans/iteration13-test-ci-deploy.md` | — | 本计划文件 |

### 修改文件（2 个）

| # | 文件 | Phase | 说明 |
|:--:|------|:--:|------|
| 1 | `docs/运维手册.md` | 3-4 | 补充 PM2 + nginx 部署说明 |
| 2 | `README.md` | 2-2 | 添加 CI badge |

### 可能修改文件（测试 setup 增强）

| # | 文件 | Phase | 说明 |
|:--:|------|:--:|------|
| 1 | `server/__tests__/setup.js` | 1-7b | 新增 `createTestProduct()` 工厂函数 |
| 2 | `server/.env.test` | 2-1 | CI 环境变量（若与现有不一致） |

---

## 十、验证清单

### Phase 1 — 测试补全

| # | 验证项 | 方法 | 目标 |
|:--:|------|------|:--:|
| 1 | 新增 8 个测试文件全部通过 | `npx vitest run` | 0 failures |
| 2 | 新增用例数 ≥ 55 | `npx vitest run --reporter=verbose` 统计 | ~57 |
| 3 | 总用例数 ≥ 220 | 172 现有 + 57 新增 ≈ 229 | ≥ 220 |
| 4 | 前端测试不受影响 | `cd miniprogram && npx vitest run` | 29 用例仍全绿 |
| 5 | 测试隔离正确 | 单独运行每个文件不依赖其他文件 | `npx vitest run <file>` × 8 |

### Phase 2 — CI 流水线

| # | 验证项 | 方法 | 目标 |
|:--:|------|------|:--:|
| 1 | 4 个 Job 全部通过 | GitHub Actions 控制台 | ✅ green |
| 2 | MySQL container 健康检查通过 | Job 日志 | MySQL ready |
| 3 | 后端测试在 CI 中通过 | `npx vitest run` 在 CI 中 0 失败 | 0 failures |
| 4 | 前端编译在 CI 中成功 | `npm run build:mp-weixin` 在 CI 中 | DONE |
| 5 | ESLint 不阻塞 CI（初期） | lint job 使用 `\|\| true` | 完成但不失败 |

### Phase 3 — 部署验证

| # | 验证项 | 方法 | 目标 |
|:--:|------|------|:--:|
| 1 | PM2 启动成功 | `pm2 start ecosystem.config.js` | status: online |
| 2 | 健康检查响应 | `curl localhost:3000/api/health` | `{"code":0}` |
| 3 | 日志正常输出 | `pm2 logs campus-market --lines 5` | 有日志内容 |
| 4 | 重启策略验证 | `pm2 restart campus-market` | 5s 内恢复 |
| 5 | nginx.conf 语法正确 | `nginx -t -c server/nginx.conf` | syntax ok |

### Phase 4 — 压力测试

| # | 验证项 | 方法 | 目标 |
|:--:|------|------|:--:|
| 1 | 健康检查 P50 < 10ms | ab 结果 | ✅ |
| 2 | 商品列表 P50 < 80ms | ab 结果 | ✅ |
| 3 | 商品详情 P50 < 50ms | ab 结果 | ✅ |
| 4 | 零失败请求 | ab 结果 Failed requests: 0 | ✅ |
| 5 | 基准文档已填写 | `docs/benchmark-results.md` | 含完整数据 |

### Phase 5 — Ship-Gate

| # | 验证项 | 方法 | 目标 |
|:--:|------|------|:--:|
| 1 | 安全扫描 0 高危 | grep + 手动审查 | 0 硬编码密钥/0 SELECT * |
| 2 | 依赖审计通过 | `npm audit --production` | 0 critical |
| 3 | 代码规范符合 | wc -l 检查 | 单文件 ≤ 500 行 |
| 4 | 文档 vs 实现一致 | API 文档交叉比对 | 无矛盾 |
| 5 | 审计报告完整 | `memory/ship-gate-audit.md` | 含 Go/No-Go 结论 |

### Phase 6 — 审核材料

| # | 验证项 | 方法 | 目标 |
|:--:|------|------|:--:|
| 1 | 5 张功能截图清晰 | 人工审查 | 可辨识界面内容 |
| 2 | 测试账号可登录 | 真机验证 | 正常登录 |
| 3 | 审核文档涵盖所有要点 | 对照微信审核指南 | 6 方面全覆盖 |
| 4 | 协议页面可访问 | 小程序中点击跳转 | 显示完整协议文本 |

---

## 十一、依赖关系

```
Phase 1 (测试补全) ──→ Phase 2 (CI) ──→ Phase 5 (Ship-Gate)
                           │
Phase 3 (部署验证) ←────────┤ (可并行)
Phase 4 (压力测试) ←────────┤ (可并行，需 Phase 3 部署完成)
Phase 6 (审核材料) ←────────┘ (可全程并行)
```

- **Phase 1 必须先完成**：CI 需要新增的测试文件
- **Phase 2 依赖 Phase 1**：CI 中 `npx vitest run` 会运行新增测试
- **Phase 3/4/6 可与 Phase 1-2 并行**：部署验证和审核材料不依赖测试补全
- **Phase 5 在所有 Phase 后**：Ship-Gate 需要全部测试通过 + CI 绿 + 部署验证

---

## 十二、工时估算

| Phase | 内容 | 预估时间 |
|:--:|------|:--:|
| 1 | 后端测试补全（8 文件 ~57 用例） | 3.0 h |
| 2 | CI 流水线配置 + 验证 | 1.5 h |
| 3 | PM2 + nginx + 部署文档 | 1.5 h |
| 4 | 压力测试 + 基准文档 | 1.0 h |
| 5 | Ship-Gate 89 项审计 | 2.0 h |
| 6 | 审核材料准备 | 1.0 h |
| **合计** | | **~10 h ≈ 2 天** |

---

## 十三、发版前最终确认清单

在全部 6 个 Phase 完成后，执行最终确认：

```bash
# 1. 全量测试
cd server && npx vitest run          # 期望：≥ 18 文件，≥ 200 用例，0 failures
cd ../miniprogram && npx vitest run   # 期望：2 文件，29 用例，0 failures

# 2. 代码质量
cd server && npx eslint src/ --ext .js
cd ../miniprogram && npx eslint src/ --ext .js,.vue

# 3. 构建
cd miniprogram && npm run build:mp-weixin   # 期望：DONE

# 4. CI 状态
# GitHub Actions → 最新 commit → ✅ green

# 5. 安全
cd server && npm audit --production        # 期望：0 critical
cd ../miniprogram && npm audit --production

# 6. 文档
# 确认 docs/ 下 16 份文档 + 本轮新增 2 份 = 18 份

# 7. 部署
pm2 start ecosystem.config.js
curl http://localhost:3000/api/health      # 期望：{"code":0,"message":"success"}
```

---

## 附录 A：测试用例汇总预测

| 阶段 | 文件数 | 用例数 |
|------|:--:|:--:|
| 第 13 轮开始前（现有） | 12 | 172 |
| Phase 1-1: auth service 单元 | +1 | +10 |
| Phase 1-2: credit service 单元 | +1 | +6 |
| Phase 1-3: notification service 单元 | +1 | +6 |
| Phase 1-4: auth middleware 单元 | +1 | +8 |
| Phase 1-5: rate-limiter middleware 单元 | +1 | +5 |
| Phase 1-6: validate middleware 单元 | +1 | +5 |
| Phase 1-7a: auth 集成 | +1 | +7 |
| Phase 1-7b: orders 集成 | +1 | +10 |
| **第 13 轮完成后** | **20** | **~229** |

---

## 附录 B：不纳入本轮的事项

以下事项在原计划中提及但经评估后不纳入本轮（理由见右侧）：

| 事项 | 原因 |
|------|------|
| `services/notification.test.js` 通知单元测试 | 已在 Phase 1-3 纳入 |
| 覆盖率 ≥ 80% | 当前覆盖率 ~45%，达到 80% 需要大量额外测试（admin/analytics/upload 等边缘模块），ROI 较低。本轮目标 70%。 |
| 前端组件测试 | StarRating/ImageUploader 已在真机验证通过，纯逻辑测试 ROI 较低 |
| `integration/health.test.js` | 单行 curl 可验证，独立测试文件意义不大 |
| `integration/reports.test.js` | report service 已有 5 单元测试，集成测试需完整工单流转（含 admin 角色），复杂度高 |
| `integration/reviews.test.js` | review service 已有 8 单元测试，覆盖创建+重复+状态校验 |
| `integration/rate-limit.test.js` | 限流测试依赖于全局状态（令牌桶），单元测试更合适 |
| `scripts/benchmark.sh` 中的搜索压测 | FULLTEXT 搜索依赖 ngram 分词器 + 真实数据量，CI 环境不适用 |
| CD（自动部署） | 超出 MVP 范围，运维手册中有手动部署步骤 |
