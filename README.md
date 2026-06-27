# 校园二手交易平台

> 广州应用科技学院肇庆校区 C2C 二手交易微信小程序  
> 前后端分离 + 双后端架构 + Spring Cloud 微服务分布式

**状态：** 全栈完成 ✅ | 测试 566+ 用例 | Docker 一键启动 | CI/CD 就绪

## 技术栈

### 前端
uni-app (Vue 3) + Pinia + SCSS → 微信小程序

### Java 分布式后端

| 类别 | 技术 | 版本 |
|------|------|------|
| 基础 | Spring Boot + Spring Cloud + Spring Cloud Alibaba | 3.2.7 / 2023.0.5 / 2023.0.1.0 |
| 网关 | Spring Cloud Gateway | 4.1.6 |
| 服务发现 | Nacos | 2.5.2 |
| 服务调用 | OpenFeign + LoadBalancer | 4.1.4 |
| 流控熔断 | Sentinel（热点参数 + 授权 + 系统自适应） | 1.8.6 |
| 分布式事务 | Seata AT 模式 | 2.0.0 |
| 消息驱动 | RocketMQ | 2.3.0 |
| 分布式锁 | Redisson（WatchDog 自动续期） | 3.25.2 |
| 缓存 | Redis + Lettuce（Cache-Aside + Token 黑名单） | 7.x |
| 数据库 | MySQL + MyBatis + Flyway | 8.0 |
| 鉴权 | Spring Security + jjwt 双 Token | 0.12.6 |
| 链路追踪 | Micrometer Tracing + Brave + Zipkin | 1.2.5 |
| 监控 | Prometheus + Grafana + Actuator | 最新 |
| 测试 | JUnit 5 + Mockito | — |
| 部署 | Docker Compose 一键启动 10 个容器 | — |

### Node.js 后端（双后端备选）
Express 5 层 + MySQL + JWT 双 Token + winston + 207 测试用例

## 架构

```
                    ┌── Nacos (:8848) 服务发现 + 配置中心
                    ├── Sentinel 流控熔断（三层防护）
                    ├── Seata AT 分布式事务
gateway (:8080) ─── ├── Redisson 分布式锁 (WatchDog)
  统一入口           ├── RocketMQ 异步消息
  JWT 鉴权           ├── Redis (:6379) Cache-Aside 缓存
                    │
                    ├── core-service (:8081)   商品/订单/用户/评价/举报
                    ├── admin-service (:8082)  管理后台/数据看板/审计
                    └── im-connector (:8083)   IM/COS STS

可观测性: Zipkin (:9411) + Prometheus (:9090) + Grafana (:3001)
```


## 快速启动 — Docker 一键部署

**前置条件：**
- JDK 17 + Maven 3.8+
- Docker Desktop
- 本地 MySQL 已启动（端口 3306）
- `.env` 文件已配置（复制 `.env.example` → `.env` 并填入密钥）

```bash
# 1. 打包
cd server-java
mvn -pl gateway,core-service,admin-service,im-connector package spring-boot:repackage -DskipTests -amd

# 2. 启动
docker compose up -d

# 3. 启动前端
cd ../miniprogram
npm install
npm run dev:mp-weixin
# 在微信开发者工具打开 miniprogram/dist/dev/mp-weixin/
```

**访问地址：**

| 服务 | 地址 |
|------|------|
| API 入口 | http://localhost:8080 |
| 健康检查 | http://localhost:8080/api/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Grafana 监控 | http://localhost:3001 (admin/admin) |
| Prometheus | http://localhost:9090 |
| Zipkin 链路追踪 | http://localhost:9411 |

**停止：**
```bash
cd server-java
docker compose down
```

**改代码后重新部署：**
```bash
mvn -pl gateway,core-service,admin-service,im-connector package spring-boot:repackage -DskipTests -amd
docker compose up -d --build
```

## 项目结构

```
campus-market/
├── miniprogram/          uni-app 前端（25 页面）
├── server/               Node.js Express 后端（可选）
├── server-java/          Java 分布式后端（主要）
│   ├── common/           共享库
│   ├── gateway/          API 网关 :8080
│   ├── core-service/     核心业务 :8081
│   ├── admin-service/    管理后台 :8082
│   ├── im-connector/     IM/COS :8083
│   ├── docker-compose.yml
│   └── Dockerfile
├── docs/                 项目文档（24+ 份）
├── rules/                14 份编码规范
└── .github/workflows/    CI/CD
```

## 从零搭建

**1. 克隆项目**
```bash
git clone <repo-url>
cd campus-market
```

**2. 配置环境变量**
```bash
cd server-java
cp .env.example .env
# 编辑 .env 填入你的 MySQL 密码、JWT 密钥、微信 AppID/AppSecret 等
```

**3. 安装依赖并启动**
```bash
# 确保本地 MySQL 已运行
# Java 后端（Docker）
docker compose up -d

# 前端
cd ../miniprogram
npm install
npm run dev:mp-weixin
```

**4. 微信开发者工具打开 `miniprogram/dist/dev/mp-weixin/`**

## 测试

```bash
# Java 后端（~330 用例）
cd server-java
mvn test

# Node.js 后端（207 用例）
cd server
npx vitest run
```

## 许可证

个人学习与校园服务项目。
