# 分布式核心能力补全计划

> Phase 13~17 — 在 Phase 0~11 基础上补齐 5 项分布式核心能力，使项目达到课程大作业满分标准。

**前置条件：** Phase 0~11 完成 ✅（common + 4 微服务 + Redis + Sentinel + 32 测试文件全部通过）

**预估工期：** 21 天（5 Phase × 3~5 天）

---

## 目标总览

| Phase | 能力 | 核心技术 | 当前状态 | 目标深度 |
|:--:|------|---------|:--:|:--:|
| 13 | 分布式事务 | Seata AT 模式 | ❌ 单服务 @Transactional | 跨服务强一致性 |
| 14 | 消息驱动 | RocketMQ | ❌ 同步 Feign 推送 | 异步解耦 + 重试 + 死信 |
| 15 | 全链路追踪 | Micrometer Tracing + Zipkin | ❌ 无 traceId | 跨 4 服务日志串联 |
| 16 | 分布式锁增强 | Redisson | ⚠️ 基础 SETNX | WatchDog + 可重入 + 公平锁 |
| 17 | 限流补全 | Sentinel 细粒度规则 | ⚠️ 仅 Gateway | 热点参数 + 授权 + 系统自适应 |

---

## Phase 13：分布式事务（Seata AT 模式）— 5 天

### 13.1 为什么需要 Seata

当前问题：`OrderService.confirm()` 在 `@Transactional` 内写 3 张表，但这 3 张表都在 core-service 内。如果后续拆服务（如独立的 `credit-service`），这笔写操作就变成跨服务分布式事务。

课程价值：Seata AT 模式是分布式事务中最易理解、与现有 MyBatis 代码兼容性最好的方案。

### 13.2 操作步骤（17 个子步骤）

#### 13.2.0 基础设施准备

| # | 操作 | 文件/位置 | 说明 |
|:--:|------|---------|------|
| 13.2.0.1 | 启动 Seata Server | Docker / 本地 | `docker run -d -p 8091:8091 -p 7091:7091 seataio/seata-server:1.7.1`；或下载二进制包 |
| 13.2.0.2 | 创建 Seata 配置 | Nacos | 在 Nacos 中创建 `seata.properties`（含 store.mode=db, db 连接信息） |
| 13.2.0.3 | 创建 undo_log 表 | Flyway 迁移 | `server-java/core-service/src/main/resources/db/migration/V003__seata_undo_log.sql` |

```sql
-- V003__seata_undo_log.sql
CREATE TABLE IF NOT EXISTS `undo_log` (
  `id`            BIGINT(20)   NOT NULL AUTO_INCREMENT,
  `branch_id`     BIGINT(20)   NOT NULL,
  `xid`           VARCHAR(128) NOT NULL,
  `context`       VARCHAR(128) NOT NULL,
  `rollback_info` LONGBLOB     NOT NULL,
  `log_status`    INT(11)      NOT NULL,
  `log_created`   DATETIME     NOT NULL,
  `log_modified`  DATETIME     NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 13.2.1 引入 Seata 依赖

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 13.2.1.1 | 父 POM 添加 Seata BOM | `server-java/pom.xml` | `seata-spring-boot-starter:1.7.1` + `seata-all:1.7.1` |
| 13.2.1.2 | core-service 添加依赖 | `core-service/pom.xml` | `seata-spring-boot-starter` |
| 13.2.1.3 | admin-service 添加依赖 | `admin-service/pom.xml` | `seata-spring-boot-starter` |

#### 13.2.2 Seata 配置

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 13.2.2.1 | Seata 配置类 | `core-service/.../config/SeataConfig.java` | `@EnableAutoDataSourceProxy` + 排除健康检查路径 |
| 13.2.2.2 | application.yml | `core-service/.../application.yml` | `seata.tx-service-group=shm-tx-group`、`seata.registry.type=nacos`、`seata.config.type=nacos` |
| 13.2.2.3 | admin-service Seata 配置 | 同上两个文件 | 与 core-service 对称配置 |
| 13.2.2.4 | application.yml | `admin-service/.../application.yml` | 同上 |

#### 13.2.3 跨服务事务改造（核心交付）

选 2 个**跨服务**写入场景改造为 Seata 全局事务：

| # | 操作 | 涉及服务 | 业务场景 |
|:--:|------|---------|---------|
| 13.2.3.1 | `OrderService.confirm()` 改造 | core → admin（Feign） | 买家确认收货 → core 更新订单状态/商品状态/信誉分 → **Feign 调用 admin 写入 admin_log**。用 `@GlobalTransactional` 包裹 |
| 13.2.3.2 | `ReportAdminService.resolveTicket()` 改造 | admin → core（Feign） | 管理员裁决举报 → admin 更新工单状态/写入 log → **Feign 调用 core 扣信誉分/封禁用户**。用 `@GlobalTransactional` 包裹 |

**改造示意（confirm 为例）：**

```java
// OrderService.java
@GlobalTransactional(name = "confirm-order", timeoutMills = 300000)
public Order confirm(Long orderId, Long userId) {
    // Phase 1: core 本地事务 — 更新 orders + products + users
    // Phase 2: Feign → admin — 写入 admin_log
    // Seata AT: 任一 Phase 失败 → 全部回滚（含 undo_log 快照回滚 + Branch 回滚）
}
```

#### 13.2.4 测试

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 13.2.4.1 | Seata 正常流程测试 | `core-service/.../SeataIntegrationTest.java` | 创建订单 → 确认 → 验证 DB + undo_log 记录 |
| 13.2.4.2 | Seata 回滚流程测试 | 同上 | Mock Feign 异常 → 验证所有写入回滚 |
| 13.2.4.3 | Seata 超时回滚测试 | 同上 | `@GlobalTransactional(timeoutMills=5000)` + 延迟 → 验证超时回滚 |

#### 13.2.5 文档

| # | 操作 | 说明 |
|:--:|------|------|
| 13.2.5.1 | Seata 使用文档 | 写入 `server-java/memory/phase13-seata.md`，含架构图 + 踩坑记录 |

---

## Phase 14：消息驱动（RocketMQ）— 5 天

### 14.1 为什么需要 RocketMQ

当前问题：所有通知都是同步 Feign 调用（`OrderService.notifyUser()` → `imConnectorFeign.sendSystemMessage()`）。IM 服务挂了会拖慢订单确认响应。而且没有消息持久化——发送失败就丢了。

课程价值：异步解耦是微服务的核心原则之一。RocketMQ 是阿里系课程标配。

### 14.2 架构设计

```
订单确认 (core-service)
  → 发送 OrderConfirmedEvent 到 RocketMQ
  → RocketMQ Broker
      ├── Consumer-A: NotificationService 写 DB 通知
      ├── Consumer-B: IM 推送服务 (im-connector)
      └── Consumer-Fail: 消费失败 → 写入 failed_system_messages 表 → 定时重试

已有资源复用：
  - failed_system_messages 表（已建，未使用）
  - FailedSystemMessage 实体（已定义，未使用）
```

### 14.3 操作步骤（21 个子步骤）

#### 14.3.0 基础设施准备

| # | 操作 | 文件/位置 | 说明 |
|:--:|------|---------|------|
| 14.3.0.1 | 启动 RocketMQ | Docker | NameServer + Broker 一键启动 |
| 14.3.0.2 | 创建 Topic | RocketMQ Console | `shm-order-event`、`shm-report-event`、`shm-notification` |

```bash
docker run -d -p 9876:9876 --name rmqnamesrv apache/rocketmq:5.1.4 sh mqnamesrv
docker run -d -p 10911:10911 -p 10909:10909 --name rmqbroker \
  -e "NAMESRV_ADDR=host.docker.internal:9876" apache/rocketmq:5.1.4 sh mqbroker
```

#### 14.3.1 公共模块准备（common）

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 14.3.1.1 | RocketMQ 自定义 Message 类 | `common/.../message/OrderEventMessage.java` | 字段：orderId, buyerId, sellerId, type(confirmed/cancelled/met), timestamp |
| 14.3.1.2 | Report 事件 Message 类 | `common/.../message/ReportEventMessage.java` | 字段：reportId, reporterId, reportedUserId, action(resolved/rejected) |
| 14.3.1.3 | Notification Message 类 | `common/.../message/NotificationMessage.java` | 字段：targetUid, messageType, title, content, payload(JSON string) |

#### 14.3.2 Producer 端（core-service + admin-service）

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 14.3.2.1 | 父 POM 添加 RocketMQ | `server-java/pom.xml` | `rocketmq-spring-boot-starter:2.3.0` |
| 14.3.2.2 | MQ 配置类 | `core-service/.../config/RocketMQConfig.java` | `RocketMQTemplate` + 全局发送超时 3000ms + 重试 3 次 |
| 14.3.2.3 | Order Event Publisher | `core-service/.../mq/OrderEventPublisher.java` | 封装 `rocketMQTemplate.syncSend("shm-order-event", msg)` |
| 14.3.2.4 | OrderService 改造 | `core-service/.../service/OrderService.java` | `confirm()` 成功 → 不再同步 Feign → 改发 MQ 消息 |
| 14.3.2.5 | Report Event Publisher | `admin-service/.../mq/ReportEventPublisher.java` | 类似封装 |
| 14.3.2.6 | ReportAdminService 改造 | `admin-service/.../service/ReportAdminService.java` | `resolveTicket()` → 发 MQ 替代同步 Feign |

#### 14.3.3 Consumer 端（core-service + im-connector + admin-service）

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 14.3.3.1 | Notification Consumer | `core-service/.../mq/NotificationConsumer.java` | 消费 `shm-order-event` → INSERT notification 表 |
| 14.3.3.2 | IM Push Consumer | `im-connector/.../mq/ImPushConsumer.java` | 消费 `shm-order-event` + `shm-report-event` → 调用 Tencent IM REST API 推送 |
| 14.3.3.3 | Consumer 重试配置 | 各 Consumer 类 | `@RocketMQMessageListener` 中配置 `maxReconsumeTimes=3` |

#### 14.3.4 失败重试与死信机制

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 14.3.4.1 | FailedMessage Repository | `common/.../repository/FailedMessageRepository.java` | 写入 `failed_system_messages` 表（复用现有表） |
| 14.3.4.2 | Dead Letter Consumer | `core-service/.../mq/DeadLetterHandler.java` | 3 次重试仍失败 → 写入 `failed_system_messages` 表 status='FAILED' |
| 14.3.4.3 | @Scheduled 重试任务 | `core-service/.../task/MessageRetryTask.java` | 每 5 分钟扫描 `failed_system_messages` 表中 `status='FAILED' AND retry_count < max_retries` 的记录，重新发送 |
| 14.3.4.4 | 订单超时自动取消 | `core-service/.../task/OrderTimeoutTask.java` | `@Scheduled(fixedDelay=60000)`：扫描 `status='pending' AND created_at < NOW() - INTERVAL 24 HOUR` → 自动 cancel |

#### 14.3.5 原有同步代码清理

| # | 操作 | 说明 |
|:--:|------|------|
| 14.3.5.1 | OrderService 去同步化 | 移除 `notifyUser()` 中的 `imConnectorFeign.sendSystemMessage()` 调用 |
| 14.3.5.2 | ReportAdminService 去同步化 | 移除 `resolveTicket()` 中的 `pushImMessage()` 同步调用 |

> **注意：** 保留 Feign 接口本身不改——UserSig 生成、COS STS 获取仍需同步 Feign。只改通知类调用。

#### 14.3.6 测试

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 14.3.6.1 | Producer 单元测试 | `core-service/.../mq/OrderEventPublisherTest.java` | Mock RocketMQTemplate → 验证 send 参数 |
| 14.3.6.2 | Consumer 单元测试 | `im-connector/.../mq/ImPushConsumerTest.java` | Mock TencentImService → 验证消费逻辑 |
| 14.3.6.3 | 端到端测试 | `core-service/.../MqIntegrationTest.java` | 发消息 → 等 2s → 验证 notification 表写入 |
| 14.3.6.4 | 死信测试 | 同上 | Mock IM 失败 3 次 → 验证 failed_system_messages 写入 |

---

## Phase 15：全链路追踪（Micrometer Tracing + Zipkin）— 4 天

### 15.1 为什么需要全链路追踪

当前问题：4 个微服务独立打 SLF4J 日志，没有任何请求 ID 串联。Gateway 收到一个慢请求，无法知道是 core-service 的 SQL 慢还是 im-connector 的网络慢。

课程价值：分布式链路追踪是微服务治理三大支柱（服务发现 + 配置中心 + 链路追踪）之一。

### 15.2 架构设计

```
Gateway (:8080)
  │  X-Trace-Id: abc123  (生成)
  │  MDC{traceId: abc123, spanId: gw-1}
  │
  ├→ core-service (:8081)
  │    X-Trace-Id: abc123  (从 Header 传播)
  │    X-Span-Id: core-1
  │    MDC{traceId: abc123, spanId: core-1}
  │
  ├→ admin-service (:8082)
  │    X-Trace-Id: abc123
  │
  └→ im-connector (:8083)
       X-Trace-Id: abc123

所有服务日志 → Zipkin (:9411) 收集
  → UI: http://localhost:9411 查看调用链 + 耗时火焰图
```

### 15.3 操作步骤（15 个子步骤）

#### 15.3.1 引入依赖

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 15.3.1.1 | 父 POM 添加版本管理 | `server-java/pom.xml` | `micrometer-tracing-bom:1.2.5` + `micrometer-tracing-bridge-brave` |
| 15.3.1.2 | 所有 4 服务 pom.xml | gateway/core/admin/im-connector | `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` |
| 15.3.1.3 | Zipkin Server | Docker | `docker run -d -p 9411:9411 openzipkin/zipkin` |

#### 15.3.2 配置

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 15.3.2.1 | Gateway application.yml | `gateway/.../application.yml` | `management.tracing.sampling.probability=1.0`（开发环境全采样）+ `spring.zipkin.base-url=http://localhost:9411` |
| 15.3.2.2 | core-service application.yml | 同上 | 同上 |
| 15.3.2.3 | admin-service application.yml | 同上 | 同上 |
| 15.3.2.4 | im-connector application.yml | 同上 | 同上 |

#### 15.3.3 MDC 增强（所有需要 traceId 的日志）

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 15.3.3.1 | Gateway MDC 过滤器 | `gateway/.../filter/TraceIdFilter.java` | `@Order(-3)`：`MDC.put("traceId", traceId)` + 响应头注入 `X-Trace-Id` |
| 15.3.3.2 | Gateway 日志格式 | `gateway/.../logback-spring.xml` | `%d{ISO8601} [%X{traceId}] [%X{spanId}] %-5level %msg%n` |
| 15.3.3.3 | core-service 日志格式 | 同上 | 同上 |
| 15.3.3.4 | admin-service 日志格式 | 同上 | 同上 |
| 15.3.3.5 | im-connector 日志格式 | 同上 | 同上 |

#### 15.3.4 关键业务日志补全

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 15.3.4.1 | core-service 关键节点打点 | `OrderService.java`, `ProductService.java`, `UserService.java` | `log.info("[{}] 开始/完成/失败", methodName)` — 自然带 traceId |
| 15.3.4.2 | Feign 调用拦截器增强 | `InternalTokenRequestInterceptor.java` | 传播 `X-Trace-Id` + `X-Span-Id` Header |
| 15.3.4.3 | Sentinel Block 日志 | `SentinelBlockHandlerConfig.java` | block 时记录 traceId（方便排查被限流的请求） |

#### 15.3.5 验证

| # | 操作 | 说明 |
|:--:|------|------|
| 15.3.5.1 | 链路完整性检查 | 用 curl 发请求到 Gateway → 在 Zipkin UI 看到完整的 3 层调用链 |
| 15.3.5.2 | 异常情况验证 | 模拟 core-service 慢 SQL → Zipkin 中该 span 显示红色 + 耗时突出 |

---

## Phase 16：分布式锁增强（Redisson）— 3 天

### 16.1 为什么需要增强

当前实现：`OrderService.create()` 中用 `redisTemplate.opsForValue().setIfAbsent()` 实现了一个基础分布式锁。存在 3 个问题：

1. **无 WatchDog** — 锁 30s 后自动过期，如果业务执行超过 30s 锁就丢了
2. **无重入** — 同一个线程再次获取同一把锁会死锁
3. **无公平性** — 多个买家同时抢一个商品，先到不一定先得

Redisson 提供开箱即用的 WatchDog（自动续期）+ 可重入锁 + 公平锁 + RedLock。

### 16.2 操作步骤（12 个子步骤）

#### 16.2.1 引入 Redisson

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 16.2.1.1 | 父 POM 添加依赖 | `server-java/pom.xml` | `redisson-spring-boot-starter:3.25.2` |
| 16.2.1.2 | core-service 添加依赖 | `core-service/pom.xml` | `redisson-spring-boot-starter` |

#### 16.2.2 Redisson 配置

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 16.2.2.1 | Redisson 配置类 | `core-service/.../config/RedissonConfig.java` | `RedissonClient` bean：单节点模式 + 序列化 `JacksonCodec` + 连接超时 3000ms + 重试 3 次 |
| 16.2.2.2 | application.yml | `core-service/.../application.yml` | `spring.redis.redisson.config` 内联配置或外置 `redisson.yaml` |

#### 16.2.3 锁工具封装

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 16.2.3.1 | 分布式锁工具类 | `common/.../util/DistributedLocker.java` | 封装 RedissonClient：`tryLock(key, waitTime, leaseTime)` + 自动续期 + finally unlock |
| 16.2.3.2 | 锁类型工厂 | 同上 | `getFairLock(key)` — 公平锁（先到先得）、`getSpinLock(key)` — 自旋锁（短操作） |

#### 16.2.4 替换现有锁

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 16.2.4.1 | OrderService 锁替换 | `core-service/.../service/OrderService.java` | 原来 `setIfAbsent` 替换为 `DistributedLocker.tryLock("lock:order:...")` |
| 16.2.4.2 | 添加公平锁场景 | `ProductService.java` 热门商品更新 | 多个用户同时抢购/举报 → `getFairLock` |

#### 16.2.5 锁监控

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 16.2.5.1 | 锁指标收集 | `common/.../util/DistributedLocker.java` | 每次 acquire/release 记录耗时（micrometer Timer） |
| 16.2.5.2 | 锁超时告警日志 | 同上 | 等待超过 5s → `log.warn("[LockWait] key={}, waitTime={}ms", key, waitTime)` |

#### 16.2.6 测试

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 16.2.6.1 | 可重入测试 | `core-service/.../DistributedLockerTest.java` | 同一线程两次获取同一锁 → 成功 |
| 16.2.6.2 | WatchDog 测试 | 同上 | 获取锁 → Sleep 35s > 原 TTL → 验证锁未释放 |
| 16.2.6.3 | 并发测试 | 同上 | 10 线程同时 acquire → 验证同一时刻仅 1 个持有锁 |
| 16.2.6.4 | 公平锁测试 | 同上 | 10 线程按顺序 acquire → 验证 FIFO 顺序 |

---

## Phase 17：限流补全（Sentinel 细粒度规则）— 4 天

### 17.1 为什么需要补全

当前状态：Sentinel 仅在 Gateway 层做了两个粗粒度资源（`sensitive-api` QPS=10，`normal-api` QPS=60）。缺失：

1. **热点参数限流** — 防止单个 productId 被刷
2. **授权规则** — 管理后台 API 限定 admin 角色
3. **系统自适应限流** — 根据服务器 LOAD/RT 动态降级
4. **核心服务层细粒度规则** — 下单、登录等高危接口独立限流
5. **兜底逻辑** — 被限流/熔断后的统一 BlockHandler 响应

### 17.2 操作步骤（14 个子步骤）

#### 17.2.1 热点参数限流

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 17.2.1.1 | ProductController 热点限流 | `core-service/.../controller/ProductController.java` | `@SentinelResource(value="product-detail", blockHandler="productDetailBlock")`，热点参数 = `productId`，单商品 QPS ≤ 20 |
| 17.2.1.2 | ProductController BlockHandler | 同上 | `productDetailBlock(Long productId, BlockException ex)` → 返回 `{code:4006, message:"该商品访问过热，请稍后再试"}` |
| 17.2.1.3 | 热点规则 Nacos 持久化 | Nacos Console | `sentinel-core-param-flow-rules` Data ID |

#### 17.2.2 授权规则

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 17.2.2.1 | 授权规则配置 | `admin-service/.../controller/UserAdminController.java` | `@SentinelResource("admin-user")` + 来源解析器（从 `X-User-Role` Header 解析）— 非 admin 角色直接 Block |
| 17.2.2.2 | RequestOriginParser | `admin-service/.../config/SentinelOriginConfig.java` | 实现 `RequestOriginParser` → 从 `X-User-Role` 提取 origin，白名单仅 `admin` |
| 17.2.2.3 | 授权规则 Nacos 持久化 | Nacos Console | `sentinel-core-auth-rules` Data ID |

#### 17.2.3 系统自适应规则

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 17.2.3.1 | 系统规则心跳 | `core-service/.../config/SentinelSystemRuleConfig.java` | `CommandLineRunner` → `SystemRuleManager.loadRules()`：入口 QPS ≤ 200、LOAD ≤ 4.0、RT ≤ 100ms、线程数 ≤ 200 |
| 17.2.3.2 | 系统规则 Nacos 持久化 | Nacos Console | `sentinel-core-system-rules` Data ID |

#### 17.2.4 统一 BlockHandler / Fallback

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 17.2.4.1 | 全局 BlockHandler 类 | `common/.../sentinel/GlobalBlockHandler.java` | 静态方法 `handleBlock(BlockException e)` → 统一返回 `{code:4006}` |
| 17.2.4.2 | 全局 Fallback 类 | `common/.../sentinel/GlobalFallback.java` | 静态方法 `handleFallback(Throwable t)` → 统一返回 `{code:6004}` |
| 17.2.4.3 | 改造现有 `@SentinelResource` | `ProductController`, `OrderController` | 引用全局 BlockHandler/Fallback 类（消除重复代码） |

#### 17.2.5 网关与服务层协同

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 17.2.5.1 | 双层级限流策略文档 | `server-java/memory/phase17-sentinel.md` | Gateway 层控总流量（QPS 60/10） + Service 层控热点参数（单商品 QPS 20） + System 层控服务器负荷。三层协同，被 Gateway 拒绝的请求不浪费服务资源 |
| 17.2.5.2 | Sentinel Dashboard 截图 + 说明 | 同上 | 展示规则列表 + 实时监控曲线 |

#### 17.2.6 测试

| # | 操作 | 文件 | 说明 |
|:--:|------|------|------|
| 17.2.6.1 | Sentinel BlockHandler 测试 | `core-service/.../controller/ProductControllerTest.java` | `@WebMvcTest` + Mock 配置 Sentinel 规则 → 并发发请求 → 验证 429 响应 |
| 17.2.6.2 | 授权规则测试 | `admin-service/.../controller/UserAdminControllerTest.java` | 用非 admin 身份访问 → 验证 Block |
| 17.2.6.3 | 限流压测脚本 | `server-java/scripts/sentinel-benchmark.sh` | wrk 压 Gateway + 验证限流生效 + 观察 Dashboard |

---

## 实施依赖关系

```
Phase 13 (Seata) ─────┐
                       ├──→ Phase 17 (Sentinel 补全)
                       │    (热点参数限流需要 Feign + Seata 两者配合)
                       │
Phase 14 (RocketMQ) ───┤
                       │
                       ├──→ Phase 15 (Tracing)
                       │    (MQ Consumer 的 Span 关联需要 Tracing 基础设施)
                       │
Phase 16 (Redisson) ───┘
  (独立，可任意时间做)
```

**推荐顺序：** 16 (Redisson) → 13 (Seata) → 14 (RocketMQ) → 15 (Tracing) → 17 (Sentinel)

理由：
- Redisson 最简单独立，先热身
- Seata 需要改业务代码，做完后再加 MQ 不会冲突
- Tracing 在所有通信链路就绪后统一接入
- Sentinel 放最后，在完整链路上配置三层限流

---

## 环境依赖（新增中间件）

| 中间件 | 版本 | 用途 | 本地端口 | Docker 命令 |
|--------|------|------|:--:|------|
| Seata Server | 1.7.1 | 分布式事务协调器 | 8091 | `docker run -d -p 8091:8091 -p 7091:7091 seataio/seata-server:1.7.1` |
| RocketMQ NameServer | 5.1.4 | 消息队列注册中心 | 9876 | `docker run -d -p 9876:9876 apache/rocketmq:5.1.4 sh mqnamesrv` |
| RocketMQ Broker | 5.1.4 | 消息队列代理 | 10911 | `docker run -d -p 10911:10911 -p 10909:10909 -e NAMESRV_ADDR=host.docker.internal:9876 apache/rocketmq:5.1.4 sh mqbroker` |
| Zipkin Server | latest | 链路追踪收集/UI | 9411 | `docker run -d -p 9411:9411 openzipkin/zipkin` |

**已有中间件（无需重新安装）：** Nacos 2.5.2 (:8848)、Redis 7.4.3 (:6379)、MySQL 8.0 (:3306)、Sentinel Dashboard (:8088)

---

## 交付物清单

| Phase | 新增 Java 文件 | 改造现有文件 | 测试文件 | 文档 |
|:--:|:--:|:--:|:--:|:--:|
| 13 (Seata) | 2 (SeataConfig ×2, SeataIntegrationTest) | 3 (OrderService, ReportAdminService, application.yml ×2, pom.xml ×2) | 1 | phase13-seata.md |
| 14 (RocketMQ) | 8 (3 Message + 3 Publisher + 2 Consumer/MQ Config) | 3 (OrderService, ReportAdminService, pom.xml ×4) | 3 | phase14-rocketmq.md |
| 15 (Tracing) | 2 (TraceIdFilter, logback ×4) | 2 (application.yml ×4, InternalTokenRequestInterceptor) | 0 (纯基础设施) | phase15-tracing.md |
| 16 (Redisson) | 2 (RedissonConfig, DistributedLocker) | 2 (OrderService, ProductService) | 1 | phase16-redisson.md |
| 17 (Sentinel) | 3 (GlobalBlockHandler, GlobalFallback, SentinelSystemRuleConfig) | 3 (ProductController, OrderController, UserAdminController) | 2 | phase17-sentinel.md |
| **合计** | **17** | **13** | **7** | **5** |

---

## 综合验证（Phase 13~17 完成后）

### 分布式能力自检表

| 能力 | 验证方法 | 预期结果 |
|------|---------|---------|
| 分布式事务 | 触发跨服务写入 → 中途 kill Feign → 查 DB | 所有涉及的表数据回滚一致 |
| 消息驱动 | 下单 → 查 notification 表 + IM 消息 | 异步延迟 < 1s，消费者失败 3 次进死信 |
| 全链路追踪 | curl → Zipkin UI | 3~4 层 span 完整串联，异常 span 标红 |
| 分布式锁增强 | 并发下单 + WatchDog 35s | 仅 1 个线程获取锁，无锁泄漏 |
| 限流三层 | wrk 压测 → Sentinel Dashboard | Gateway 429 正确返回，热点参数 BlockHandler 生效，系统负载高时自动降级 |

### 最终测试

```bash
# 全量测试
mvn test  # 目标：39 测试文件（原有 32 + 新增 7），全部 PASS

# 全量回归（确保未破坏现有功能）
mvn -pl common,core-service,admin-service,im-connector,gateway test

# 前端 build 验证
cd miniprogram && npm run build:mp-weixin
```

### 全部完成后项目最终指标

| 指标 | 当前 | 完成后 |
|------|:--:|:--:|
| 微服务数 | 4 | 4 |
| 中间件数量 | 4 (MySQL+Nacos+Redis+Sentinel) | 7 (+Seata+RocketMQ+Zipkin) |
| 测试文件数 | 32 | 39 |
| 分布式能力覆盖率 | 5/10 (50%) | 9/10 (90%) |
| 课程作业匹配度 | 7.5/10 | **9.5/10** |
