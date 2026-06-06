# ADR-0003：JWT 双 Token 鉴权机制（access + refresh）

- **日期：** 2026-06-04
- **状态：** 已采纳
- **决策者：** 单人开发

## 背景

小程序用户登录后需要无感维持会话。微信小程序不支持 Cookie-based session，需要无状态鉴权方案。同时需要兼顾安全性（Token 泄漏后能快速失效）和用户体验（不需要频繁重新登录）。

## 决策

**JWT 双 Token 机制：短期 access_token（15 min）+ 长期 refresh_token（7 d）。**

- access_token: 15 分钟有效期，用于所有 API 请求鉴权
- refresh_token: 7 天有效期，仅用于刷新 access_token
- Token 存储在客户端本地 Storage
- 登录接口返回双 Token，access_token 过期时客户端用 refresh_token 静默刷新
- 服务端 `auth` 中间件验证 access_token 签名 + 有效期

## 考虑的替代方案

| 方案 | 优点 | 缺点 | 为何未选 |
|------|------|------|---------|
| 单一长期 access_token（30 d） | 实现简单，无需刷新逻辑 | Token 泄漏后 30 天内攻击者可持续使用——安全风险高 | 不符合安全规范要求 |
| 短期 access_token 无刷新 | 最安全 | 15 分钟过期后用户需重新登录——体验极差 | 不合理 |
| Session + Redis | 服务端可控，可随时踢人 | 需 Redis 依赖、小程序不支持 Cookie、服务端存储增加成本 | 过度设计，单人项目无需 Redis |
| access_token 存数据库，每次请求查库 | 可随时吊销 | 每次请求多一次数据库查询 | 无状态优势丧失，性能差 |

## 影响范围

- `server/src/middleware/auth.ts` — JWT 验证中间件
- `server/src/controllers/auth.ts` — 登录/刷新/获取用户信息
- `server/src/config/` — JWT 密钥配置（ACCESS_SECRET / REFRESH_SECRET / EXPIRES）
- `miniprogram/api/` — 客户端 Token 存储 + 过期自动刷新拦截器
- 环境变量: `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`, `JWT_ACCESS_EXPIRES`, `JWT_REFRESH_EXPIRES`

## 后果

### 正面
- 无状态：服务端不存储会话，易于水平扩展
- 安全与体验平衡：短期 access + 静默刷新
- 微信小程序天然适合（Storage 存储 + Authorization Header 传输）
- 符合 RESTful 无状态原则

### 负面
- Token 签发后无法主动吊销（只能等过期）——黑名单方案需要额外存储
- refresh_token 泄漏风险高于 session 方案
- Token 体积较大（相比 session_id），每次请求多传输 ~200 bytes
- 需要客户端实现刷新拦截逻辑

## 相关

- 关联文档: [技术架构文档 §五-六](../技术架构文档.md)
- 关联规范: [security-rules](../../rules/security-rules.md)
