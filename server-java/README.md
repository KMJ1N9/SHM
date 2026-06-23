# Campus Market — Java 分布式后端

校园二手交易平台（广州应用科技学院肇庆校区 C2C 二手交易平台）的 Spring Cloud 微服务后端，与 Node.js 后端共享同一 API 契约，可无缝切换。

## 架构概览

```
小程序/前端
    │
    ▼
┌──────────────────────────────────────────────────┐
│  Gateway (:8080)                                  │
│  Spring Cloud Gateway — 统一入口                  │
│  JWT 鉴权 + CORS + 路由转发 + Sentinel 流控       │
└──────┬──────────────┬──────────────┬──────────────┘
       │              │              │
       ▼              ▼              ▼
┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│ Core        │ │ Admin       │ │ IM          │
│ (:8081)     │ │ (:8082)     │ │ (:8083)     │
│ 商品/订单   │ │ 管理后台    │ │ IM UserSig  │
│ 用户/评价   │ │ 数据看板    │ │ COS STS     │
│ 举报/搜索   │ │ 敏感词库    │ │ 图片上传    │
└──────┬──────┘ └──────┬──────┘ └──────┬──────┘
       │               │               │
       └───────────────┼───────────────┘
                       │
       ┌───────────────┼───────────────┐
       ▼               ▼               ▼
┌──────────┐  ┌────────────┐  ┌──────────────┐
│ MySQL    │  │ Redis      │  │ Nacos :8848   │
│ :3306    │  │ :6379      │  │ 服务发现+配置 │
│ 14 表    │  │ 缓存/锁    │  │               │
└──────────┘  └────────────┘  └──────────────┘
```

**技术栈：** Spring Boot 3.2.7 + Spring Cloud 2023.0.5 + Spring Cloud Alibaba 2023.0.1.0 + MyBatis 3.0.4 + MySQL 8.0 + Redis + Nacos 2.5 + Sentinel + Seata 2.0 + Redisson 3.25 + Flyway + JUnit 5 + Mockito

**模块：**

| 模块 | 端口 | 职责 |
|:------|:--:|:------|
| `common` | — | 共享库：实体类/DTO/ErrorCode/ResponseBuilder/JwtUtil/SensitiveWordFilter |
| `gateway` | 8080 | API 网关：路由转发 + JWT 鉴权 + CORS + Sentinel 流控 |
| `core-service` | 8081 | 核心业务：商品/订单/用户/评价/举报/通知/信誉分（含 MyBatis Mapper XML） |
| `admin-service` | 8082 | 管理后台：用户管理/商品下架/数据看板/审计日志/敏感词库 |
| `im-connector` | 8083 | IM + COS 连接器：UserSig 生成 / COS STS 临时密钥 / 图片上传 |

## 环境要求

| 组件 | 版本 | 说明 |
|:------|:------|:------|
| JDK | **17+** | 编译和运行均需 Java 17 |
| Maven | **3.9+** | 构建管理 |
| MySQL | **8.0+** | 数据库，需预先创建空库 |
| Redis | **7.4+** | 缓存 + Token 黑名单 + 分布式锁 |
| Nacos | **2.5+** | 服务发现 + 配置中心（必须启动） |

## 快速开始

### 1. 克隆项目

```bash
git clone <repo-url>
cd campus-market/server-java
```

### 2. 配置环境变量

复制 `.env.example`（如存在）或直接编辑 `.env`：

```bash
cp .env.example .env   # 如有模板文件
# 编辑 .env 填入以下必需变量
```

**.env 必需变量：**

```bash
# MySQL
DB_URL=jdbc:mysql://127.0.0.1:3306/campus_market_dev?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8
DB_USERNAME=root
DB_PASSWORD=your_password

# JWT（生成方式：openssl rand -hex 32）
JWT_ACCESS_SECRET=your_access_secret
JWT_REFRESH_SECRET=your_refresh_secret

# 微信小程序
WX_APP_ID=your_wechat_appid
WX_APP_SECRET=your_wechat_secret

# 腾讯云 IM
IM_SDK_APP_ID=your_im_sdk_appid
IM_SECRET_KEY=your_im_secret_key

# 腾讯云 COS
COS_BUCKET=your_bucket
COS_REGION=ap-guangzhou
COS_SECRET_ID=your_cos_secret_id
COS_SECRET_KEY=your_cos_secret_key

# Nacos
NACOS_SERVER_ADDR=localhost:8848
NACOS_DISCOVERY_ENABLED=true
```

> **注意：** `.env` 文件包含真实密钥，已通过 `.gitignore` 排除，**切勿提交到版本控制**。
>
> 环境变量通过 `DotenvEnvironmentPostProcessor` 在应用启动时自动注入 Spring 的 `Environment`，无需手动 `export`。所有 `application.yml` 中的敏感值均通过 `${ENV_VAR}` 占位符引用。

### 3. 启动基础设施

```bash
# 启动 MySQL（确保已创建数据库 campus_market_dev）
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS campus_market_dev DEFAULT CHARACTER SET utf8mb4"

# 启动 Redis
redis-server                    # 或 Windows: redis-server.exe

# 启动 Nacos（单机模式）
# Linux/Mac:
sh nacos/bin/startup.sh -m standalone
# Windows:
nacos\bin\startup.cmd -m standalone
```

### 4. 编译项目

```bash
mvn clean compile
```

### 5. 启动服务（按顺序）

服务启动顺序：core → admin → im-connector → gateway

```bash
# 终端 1：Core Service
mvn -pl core-service spring-boot:run     # http://localhost:8081

# 终端 2：Admin Service
mvn -pl admin-service spring-boot:run    # http://localhost:8082

# 终端 3：IM Connector
mvn -pl im-connector spring-boot:run     # http://localhost:8083

# 终端 4：Gateway（统一入口）
mvn -pl gateway spring-boot:run          # http://localhost:8080
```

**验证启动：**

```bash
# 健康检查（直接访问 core-service）
curl http://localhost:8081/api/health

# 健康检查（通过 Gateway）
curl http://localhost:8080/api/health

# 期望响应：{"code": 0, "message": "ok", "data": {"status": "UP"}}
```

### 6. 数据库迁移

Flyway 在 `core-service` 启动时自动执行迁移（`V001__initial_schema.sql` 等），无需手动干预。迁移脚本位于 `core-service/src/main/resources/db/migration/`。

> **幂等性保证：** 所有迁移脚本使用 `CREATE TABLE IF NOT EXISTS`，重复执行安全。

## API 文档（Swagger UI）

启动所有服务后访问：

| 入口 | 地址 | 说明 |
|:------|:------|:------|
| **Gateway 聚合** | http://localhost:8080/swagger-ui.html | 统一入口，可在右上角切换服务 |
| Core Service 直连 | http://localhost:8081/swagger-ui.html | 核心业务 API |
| Admin Service 直连 | http://localhost:8082/swagger-ui.html | 管理后台 API |
| IM Connector 直连 | http://localhost:8083/swagger-ui.html | IM/COS 连接器 API |

## 测试

```bash
# 全量测试（所有模块）
mvn test

# 仅 common 模块
mvn -pl common test

# 仅 core-service
mvn -pl core-service test

# 单个测试类
mvn -pl core-service test -Dtest="AuthServiceTest"

# 测试覆盖率（需 JaCoCo 插件）
mvn test jacoco:report
```

## 项目结构

```
server-java/
├── pom.xml                          # 父 POM（依赖版本管理）
├── README.md                        # 本文件
├── .env                             # 环境变量（不入库）
├── IMPLEMENTATION-PLAN.md           # 12 Phase 详细实施计划
├── DISTRIBUTED-CAPABILITIES-PLAN.md # Phase 13~17 分布式能力计划
├── common/                          # 共享库
│   └── src/main/java/com/shm/common/
│       ├── model/entity/            # 14 个实体类（与 DB 表一一对应）
│       ├── model/dto/               # 数据传输对象
│       ├── exception/               # ErrorCode（30 枚举）+ BusinessException
│       ├── util/                    # JwtUtil / ResponseBuilder / SensitiveWordFilter
│       └── config/                  # JacksonConfig / MyBatisConfig
├── gateway/                         # Spring Cloud Gateway
│   └── src/main/java/com/shm/gateway/
│       ├── filter/                  # JwtAuthGatewayFilter
│       └── config/                  # GatewayConfig / CorsConfig / SentinelGatewayConfig
├── core-service/                    # 核心业务服务
│   └── src/main/
│       ├── java/com/shm/core/
│       │   ├── controller/          # 10 个 Controller
│       │   ├── service/             # 12 个 Service
│       │   ├── repository/          # 6 个 Repository
│       │   ├── security/            # JwtAuthFilter + CurrentUser
│       │   └── config/              # SecurityConfig / RedisConfig / SeataConfig
│       └── resources/
│           ├── mapper/              # MyBatis XML（5 个 Mapper）
│           └── db/migration/        # Flyway 迁移脚本
├── admin-service/                   # 管理后台服务
│   └── src/main/java/com/shm/admin/
│       ├── controller/              # 管理员 API
│       ├── service/                 # LogService / ReportAdminService
│       └── security/                # JWT 鉴权 + 角色校验
└── im-connector/                    # IM + COS 连接器
    └── src/main/java/com/shm/im/
        ├── controller/              # UserSig / COS STS / 上传
        └── service/                 # UserSigService / CosStsService
```

## 与 Node.js 后端的关系

- **相同 API 契约：** 43 个端点路径一致、响应格式一致（`{ code, message, data }`）、错误码体系一致（30 个 ErrorCode）
- **前端零改动切换：** 修改 [miniprogram/src/api/index.js](../miniprogram/src/api/index.js) 中的 `BASE_URL`，从 `localhost:3000` 切到 `localhost:8080` 即可
- **详细对比：** 参见 [docs/双后端切换说明.md](../docs/双后端切换说明.md)

## 关键设计决策

| 决策 | 选择 | 原因 |
|:------|:------|:------|
| ORM | MyBatis + Mapper XML | 与 Node.js 原生 SQL 风格一致，比 JPA 更透明 |
| 鉴权 | JWT 双 Token + Spring Security | Gateway 层统一验证，下游服务二次校验 |
| 分布式事务 | Seata AT 模式 | 跨服务数据一致性（订单创建 + 通知推送） |
| 分布式锁 | Redisson RLock | 替代 SETNX，WatchDog 自动续期防死锁 |
| 缓存 | Redis Cache-Aside | `@Cacheable` + `@CacheEvict`，T TL 5~30min |
| 数据库迁移 | Flyway 幂等建表 | `CREATE TABLE IF NOT EXISTS`，兼容重复执行 |
| API 文档 | SpringDoc OpenAPI 2.3 | Swagger UI 聚合，Gateway 统一入口 |

## 开发进度

| Phase | 内容 | 状态 |
|:------|:------|:--:|
| 0 | 环境验证 | ✅ |
| 1 | 项目骨架 | ✅ |
| 2 | 数据库层（14 表 DDL + MyBatis） | ✅ |
| 3 | 认证模块（JWT 双 Token + Spring Security） | ✅ |
| 4 | 核心业务（商品/订单/用户/评价/举报） | ✅ |
| 5 | 管理后台 | ✅ |
| 6 | IM 连接器（UserSig + COS STS） | ✅ |
| 7 | API 网关（路由 + JWT 鉴权） | ✅ |
| 8 | 中间件 + 契约验证 | ✅ |
| 9 | Sentinel 流控熔断 | ✅ |
| 10 | Redis 缓存层 | ✅ |
| 11 | 测试与回归（330 test, 326 pass） | ✅ |
| 12 | 文档与交付（SpringDoc + README） | 🔄 |
| 13 | Seata 分布式事务 | ✅ |
| 14 | RocketMQ 消息驱动 | ❌ |
| 15 | Zipkin 全链路追踪 | ❌ |
| 16 | Redisson 分布式锁增强 | ✅ |
| 17 | Sentinel 限流补全 | ❌ |
