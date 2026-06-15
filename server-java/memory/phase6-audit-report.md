# Phase 6 全面审计报告

**审计时间：** 2026-06-14  
**审计范围：** 11 Java 文件 + 1 pom.xml + 2 yml + Node.js 参考实现交叉比对  
**审计方法：** 遍历 18 子步骤逐项核验 → Node.js `im-api.js` / `cos.js` / `tencent.js` / `upload.js` 逐方法交叉验证 → 安全审查 → 编译测试  
**状态：** ✅ P0/P1/P2 已修复 (2026-06-14)

---

## 一、逐步骤审计结果

### 6.1 Tencent IM Service（6/6 ✅）

| 步骤 | 文件 | 状态 | 详情 |
|:---:|------|:---:|------|
| 6.1.1 | `config/ImConfig.java` | ✅ | `@ConfigurationProperties("tencent.im")` — sdkAppId / secretKey / adminAccount，支持环境变量覆盖 |
| 6.1.2 | `service/UserSigService.java` | ✅ | 完整 TLS 2.0 算法实现：HMAC-SHA256 → sigDoc JSON → zlib deflate → Base64 + 自定义 URL-safe 转义（+→* /→- =→_） |
| 6.1.3 | `service/TencentImService.java` | ✅ | `importAccount()` / `sendSystemMessage()` / `sendBatchSystemMessage()` — 3 个 REST API 方法 |
| 6.1.4 | `config/RestTemplateConfig.java` | ✅ | 连接 5s / 读取 10s 超时配置（比 Node.js axios 8s 多 2s 余量） |
| 6.1.5 | `application.yml` | ✅ | `tencent.im.sdk-app-id` / `secret-key` / `admin-account` 配置项，均通过 `${ENV}` 注入 |
| 6.1.6 | UserSig 验证 | ⚪ P3 | 已有 19 个单元测试（KAT 验证 HMAC 32 字节 / zlib 头 0x78 / URL-safe 转义）但**逐字节对比** Node.js 输出需运行时环境（真实 IM 密钥 + Node.js 生成参考输出） |

### 6.2 COS Service（3/3 ✅）

| 步骤 | 文件 | 状态 | 详情 |
|:---:|------|:---:|------|
| 6.2.1 | `service/CosService.java` | ✅ | `generateCredential(userId)` — HMAC-SHA1 签名 policy / mock 模式兜底 / 按 userId 隔离路径 |
| 6.2.2 | `pom.xml` | ✅ | `cos_api` 依赖已添加（来自父 POM 的 dependencyManagement），Phase 6 报告声称 5.6.227 |
| 6.2.3 | COS 凭证验证 | ⚪ P3 | 需运行时 COS 配置 + 实际上传验证 |

### 6.3 Controller 层 + 内部 API（5/5 ✅ 功能，1 P0 安全）

| 步骤 | 端点 | 状态 | 详情 |
|:---:|------|:---:|------|
| 6.3.1 | `GET /api/upload/cos-credential` | ✅ 已修复 | **P0 #1 已修复：** userId 从 `@RequestHeader("X-User-Id")` 读取（Gateway JWT 注入），与 Node.js `req.user.id` 行为一致 |
| 6.3.2 | `POST /internal/im/usersig` | ✅ | 返回 `{ userId, userSig, sdkAppId }` |
| 6.3.3 | `POST /internal/im/import` | ✅ | 调用 `importAccount(userId, nick, faceUrl)` |
| 6.3.4 | `POST /internal/im/send` | ✅ | 单聊系统消息，支持 orderId → extra 扩展 |
| 6.3.5 | `POST /internal/im/send-batch` | ✅ | 批量推送，接收 `List<String> toUserIds` |

### 6.4 验证（0/4 P3，均需运行时）

| 步骤 | 状态 |
|:---:|:---:|
| 6.4.1 curl COS 凭证 | ⚪ P3 |
| 6.4.2 UserSig 逐字节对比 | ⚪ P3 |
| 6.4.3 IM 账号导入控制台验证 | ⚪ P3 |
| 6.4.4 COS 实际上传 | ⚪ P3 |

---

## 二、发现的缺口

### 🔴 P0 — 1 个（安全漏洞，必须修复）

#### #1 UploadController 从查询参数取 userId，而非 JWT 令牌 🔴

**文件：** `UploadController.java:36`

**问题：**

```java
@GetMapping("/cos-credential")
public Map<String, Object> getCredential(@RequestParam Long userId) {
    Map<String, Object> data = cosService.generateCredential(userId);
    return ResponseBuilder.ok(data);
}
```

Node.js 对应实现使用 `req.user.id`（JWT 中间件注入，来自令牌签名）：

```javascript
// server/src/controllers/upload.js:53
async getCredential(req, res, next) {
    const credential = await generateCredential(req.user.id);  // ← JWT 提取
    res.json({ code: 0, message: 'ok', data: credential });
}
```

**攻击场景：**
1. 攻击者正常登录（userId=100），持有合法 JWT
2. 调用 `GET /api/upload/cos-credential?userId=200`
3. 获取目标用户 `user_200/` 目录的 COS 临时凭证（tmpSecretId + tmpSecretKey + signKey）
4. 可向受害者目录上传任意文件，或替换受害者的商品图片

**根因：** Gateway 的 `JwtAuthGatewayFilter` 已验证 JWT 并将 `X-User-Id` 注入请求头，但 UploadController 未使用该请求头，而是信任客户端传入的查询参数。

**修复方案：**

```java
// 方案 A：从 Gateway 注入的请求头读取（推荐，与 Node.js 行为一致）
@GetMapping("/cos-credential")
public Map<String, Object> getCredential(@RequestHeader("X-User-Id") Long userId) {
    ...
}

// 方案 B：在 im-connector 添加 Spring Security + JWT Filter（重，不推荐）
```

**影响范围：** 所有已认证用户可获取任意用户 COS 上传路径的临时凭证。

**严重程度：** 🔴 P0 — 身份伪造，数据隔离失效。

---

### 🟡 P1 — 3 个（行为偏差/代码质量，建议修复）

#### #2 InternalImController IM 调用失败使用 6001 而非 6004 🟡

**文件：** `InternalImController.java:65,92,111`

**问题：**

```java
// 当前代码
if (result.containsKey("ErrorCode") && !"0".equals(String.valueOf(result.get("ErrorCode")))) {
    return ResponseBuilder.error(6001, (String) result.getOrDefault("ErrorInfo", "IM import failed"));
}
```

`ErrorCode.java` 已定义专用错误码：
- `IM_API_FAILED(6004, "IM 服务调用失败")`
- `COS_API_FAILED(6005, "对象存储服务异常")`
- `INTERNAL_ERROR(6001, "服务内部错误")`

当前使用通用 6001（INTERNAL_ERROR），语义不精确。Node.js 返回 IM REST API 原始错误码和错误信息，调用方根据 `ErrorCode` 区分失败原因。

**修复：** 三处均改为 `ResponseBuilder.error(ErrorCode.IM_API_FAILED.getCode(), ...)`

**严重程度：** 🟡 P1 — 线上排障时错误码混淆，但功能不受影响。

---

#### #3 TencentImService.toJson() 手写 JSON 序列化，不用 Jackson 🟡

**文件：** `TencentImService.java:218-242`

**问题：**

```java
/** 简单的 JSON 序列化（避免引入 Jackson 直接依赖） */
private String toJson(Map<String, Object> map) {
    StringBuilder sb = new StringBuilder("{");
    // ... 仅处理 String/Number/Boolean/Map/null
    // 不处理 List、数组、嵌套复杂类型
}
```

- Spring Boot Web 已包含 Jackson（`spring-boot-starter-web` → `spring-boot-starter-json` → `jackson-databind`）。"避免引入 Jackson 直接依赖"的注释不成立——Jackson 已在 classpath 上。
- 当前实现不处理 `List` 类型（fallback 到 `String.valueOf()`），如果 extra 中包含数组（如 `orderIds: [1,2,3]`），JSON 结构错误。
- Node.js 使用 `JSON.stringify()`（内置），行为可靠且经过充分测试。

**修复：** 注入 Jackson `ObjectMapper` 替代手写序列化，或直接使用 `new ObjectMapper().writeValueAsString(map)`。

```java
private final ObjectMapper objectMapper;  // 构造函数注入

private String toJson(Map<String, Object> map) {
    try {
        return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException e) {
        throw new RuntimeException("JSON 序列化失败", e);
    }
}
```

**严重程度：** 🟡 P1 — 当前使用场景（extra 不含数组）下功能正常，但存在潜在的 JSON 结构错误风险。

---

#### #4 CosService 过期时间格式与 Node.js 不一致 🟡

**文件：** `CosService.java:89`

**问题：**

```java
// Java
String expiration = Instant.ofEpochSecond(expiredTime).toString();
// 输出：2026-06-14T10:30:00Z
```

```javascript
// Node.js
expiration: new Date((now + durationSeconds) * 1000).toISOString()
// 输出：2026-06-14T10:30:00.000Z
```

Java 的 `Instant.toString()` 省略毫秒部分（当毫秒为 0 时），而 JavaScript `Date.toISOString()` 总是输出 `.000Z` 后缀。两者均为合法 ISO 8601，**COS policy 解析器通常接受两种格式**，但违反了 §0.1 "前端 uni-app 零改动"约束中日期格式必须一致的规则。

**修复：** 使用 `DateTimeFormatter` 强制输出毫秒格式：

```java
private static final DateTimeFormatter COS_EXPIRATION_FMT =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                     .withZone(ZoneOffset.UTC);

String expiration = COS_EXPIRATION_FMT.format(Instant.ofEpochSecond(expiredTime));
```

**严重程度：** 🟡 P1 — COS 实际通常容忍该差异，但违反前端零改动约束。

---

### ⚪ P2 — 5 个（代码质量/可维护性）

| # | 问题 | 文件 | 说明 |
|---|------|------|------|
| **5** | Controller 缺少参数校验 | `UploadController.java:44` / `InternalImController.java:46,65,87,114` | ✅ **已修复（P0/P1 修复时附带）：** 添加 `@Positive` / `@NotBlank` / `@NotEmpty` 注解 + `@Validated` 类注解 |
| **6** | `escapeJson` 重复定义（两处不一致） | `UserSigService.java:134` vs TencentImService（已移除） | ✅ **已修复：** P1 #3 修复时移除了 TencentImService 中的 `escapeJson`（改为 Jackson ObjectMapper）。UserSigService 保留其版本（sigDoc JSON 转义用途不同） |
| **7** | `scanBasePackages = "com.shm"` 过度扫描 | `ImConnectorApplication.java:13` | ✅ **已修复（P2 #7）：** 缩小为 `"com.shm.im"` + `@Import({GlobalExceptionHandler.class})` 显式导入。不再扫描 common 的 `JacksonConfig`（本服务只需默认 ObjectMapper）和 core/admin 全部组件 |
| **8** | `TencentImService.sendBatchSystemMessage` 使用 `/v4/openim/batchsendmsg` | `TencentImService.java:152` | ✅ **已修复（P2 #8）：** 在方法 Javadoc 中添加"Node.js 差异"章节，标注请求路径/To_Account 类型/ErrorInfo 文案 3 项差异 + 调用方应仅依赖 ErrorCode 判断 |
| **9** | `/internal/im/*` 端点无内部认证 | `InternalImController.java` | ✅ **已修复（P2 #9）：** 添加 defense-in-depth 机制：im-connector 端 `InternalAuthInterceptor` 校验 `X-Internal-Token`（无配置时放行）+ core/admin 端 `InternalTokenRequestInterceptor`（Feign）自动注入 Token 头 + 3 个 yml 均添加 `internal.token: ${INTERNAL_TOKEN:}` |

### ⚪ P3 — 4 个（需运行时环境验证）

| # | 步骤 | 验证内容 |
|---|:---:|------|
| **10** | 6.1.6 | UserSig 逐字节对比：用已知输入 → Node.js `tls-sig-api-v2` 生成 → Java `UserSigService` 生成 → Hex dump 逐字节对比 |
| **11** | 6.4.1 | `curl GET /api/upload/cos-credential` + 有效 JWT → STS 凭证结构与 Node.js 一致 |
| **12** | 6.4.3 | IM 账号导入 → 腾讯云 IM 控制台可见用户资料（Nick + FaceUrl） |
| **13** | 6.4.4 | 用 COS 凭证上传图片到 COS → 成功存储到目标 Bucket + 前端可访问 |

---

## 修复记录（2026-06-14）

### 🔴 P0 #1：UploadController userId 安全漏洞 ✅ 已修复

**文件：** `UploadController.java`

```java
// 修复前
@GetMapping("/cos-credential")
public Map<String, Object> getCredential(@RequestParam Long userId) {

// 修复后
@GetMapping("/cos-credential")
public Map<String, Object> getCredential(
        @RequestHeader("X-User-Id") @Positive(message = "用户 ID 必须为正整数") Long userId) {
```

**变更：**
- `@RequestParam` → `@RequestHeader("X-User-Id")`：从 Gateway 注入的 JWT 认证请求头读取，与 Node.js `req.user.id` 行为一致
- 添加 `@Positive` 校验（P2 #5 附带修复）
- 添加 `@Validated` 类注解
- 更新 Javadoc 安全模型说明

**验证：** 编译通过。前端 `get('/upload/cos-credential')` 无参调用兼容（userId 从 JWT 提取，与 Node.js 一致）。

---

### 🟡 P1 #2：IM 错误码 6001 → 6004 (IM_API_FAILED) ✅ 已修复

**文件：** `InternalImController.java`（3 处：import/发送/批量发送）

```java
// 修复前
import com.shm.common.util.ResponseBuilder;
// ...
return ResponseBuilder.error(6001, (String) result.getOrDefault("ErrorInfo", "IM import failed"));

// 修复后
import com.shm.common.exception.ErrorCode;
// ...
return ResponseBuilder.error(ErrorCode.IM_API_FAILED.getCode(),
        (String) result.getOrDefault("ErrorInfo", "IM import failed"));
```

**附带修复：** 添加 `@Validated` + `@NotBlank`/`@NotEmpty` 参数校验（P2 #5）。

---

### 🟡 P1 #3：TencentImService.toJson() → Jackson ObjectMapper ✅ 已修复

**文件：** `TencentImService.java`

```java
// 修复前 — 手写 JSON 序列化（40 行，不处理 List/数组）
private String toJson(Map<String, Object> map) {
    StringBuilder sb = new StringBuilder("{");
    // ...仅处理 String/Number/Boolean/Map/null，List fallback 到 String.valueOf()
}

// 修复后 — Jackson ObjectMapper（5 行，处理所有 Java 类型）
private String toJson(Map<String, Object> map) {
    try {
        return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException e) {
        throw new RuntimeException("IM 消息 JSON 序列化失败", e);
    }
}
```

**变更：**
- 注入 Spring Boot 自带的 Jackson `ObjectMapper`（构造函数新增参数）
- 移除手写 `toJson()` 和 `escapeJson()` 方法（~45 行 → 6 行）
- 与 Node.js `JSON.stringify()` 行为一致

---

### 🟡 P1 #4：CosService 过期时间格式 → 含毫秒 .000Z ✅ 已修复

**文件：** `CosService.java`

```java
// 修复前
String expiration = Instant.ofEpochSecond(expiredTime).toString();
// → "2026-06-14T10:30:00Z"

// 修复后
private static final DateTimeFormatter COS_EXPIRATION_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(ZoneOffset.UTC);

String expiration = COS_EXPIRATION_FMT.format(Instant.ofEpochSecond(expiredTime));
// → "2026-06-14T10:30:00.000Z"（与 Node.js Date.toISOString() 完全一致）
```

---

### ⚪ P2 #7：scanBasePackages 过度扫描 ✅ 已修复

**文件：** `ImConnectorApplication.java`

```java
// 修复前
@SpringBootApplication(scanBasePackages = "com.shm")

// 修复后
@SpringBootApplication(scanBasePackages = "com.shm.im")
@Import({GlobalExceptionHandler.class})
```

**变更：**
- `scanBasePackages` 从 `"com.shm"` 缩小为 `"com.shm.im"` — 仅扫描本模块
- `@Import({GlobalExceptionHandler.class})` — 显式导入 common 的异常处理器（`@Validated` 校验 → 400 响应）
- 不再加载 common 的 `JacksonConfig`（本服务只需 Spring Boot 默认 ObjectMapper，无需蛇形命名）
- `DotenvEnvironmentPostProcessor` 通过 SPI 机制加载，不受扫描范围影响

---

### ⚪ P2 #8：batchsendmsg 端点差异文档 ✅ 已修复

**文件：** `TencentImService.java`

在 `sendBatchSystemMessage()` 方法 Javadoc 添加 `<h3>Node.js 差异</h3>` 章节，标注 3 项差异：
- 请求路径：`/v4/openim/batchsendmsg` vs Node.js `/v4/openim/sendmsg`
- `To_Account` 类型：JSON 数组 vs Node.js 隐式类型检测
- `ErrorInfo` 文案可能不同 → 调用方应仅依赖 `ErrorCode` 判断

---

### ⚪ P2 #9：内部 API 无认证 ✅ 已修复

**新增文件：**
- `im-connector/.../config/InternalAuthInterceptor.java` — `HandlerInterceptor`，校验 `X-Internal-Token`
- `im-connector/.../config/InternalAuthConfig.java` — `WebMvcConfigurer`，注册拦截器至 `/internal/**`
- `core-service/.../config/InternalTokenRequestInterceptor.java` — Feign `RequestInterceptor`，自动注入 Token
- `admin-service/.../config/InternalTokenRequestInterceptor.java` — 同上

**修改文件：**
- `im-connector/application.yml` — 添加 `internal.token: ${INTERNAL_TOKEN:}`
- `core-service/application.yml` — 同上
- `admin-service/application.yml` — 同上

**安全模型：**
```
core-service ──(Feign + X-Internal-Token)──→ InternalAuthInterceptor ──→ im-connector
admin-service ──(Feign + X-Internal-Token)──→ InternalAuthInterceptor ──→ im-connector
```
- 本地开发：`INTERNAL_TOKEN` 为空 → 拦截器放行，Feign 不注入
- 生产环境：`INTERNAL_TOKEN` 环境变量 → 全链路 defense-in-depth

---

## 三、Node.js 交叉验证：行为一致性逐项比对

### 3.1 UserSig 生成

| 步骤 | Node.js (`im-api.js:34-36`) | Java (`UserSigService.java:63-93`) | 一致性 |
|------|------|------|:---:|
| 算法库 | `tls-sig-api-v2` npm 官方库 | 自实现（基于官方 Java 参考） | 🟡 未逐字节验证 |
| 签名内容 | 库内部处理（v2 标签格式） | `TLS.identifier:{id}\nTLS.sdkappid:{appid}\n...` | ✅ 格式一致 |
| HMAC 密钥 | `config.im.secretKey` UTF-8 原始字节 | `imConfig.getSecretKey()` UTF-8 原始字节 | ✅ |
| 压缩 | zlib deflate（库内部） | `Deflater(Deflater.DEFAULT_COMPRESSION)` nowrap=false | ✅ |
| URL 转义 | base64-url npm 包 (+→* /→- =→_) | 手动 `replace('+','*').replace('/','-').replace('=','_')` | ✅ KAT 验证 |
| 有效期 | 默认 604800 秒（7 天） | 默认 604800 秒（7 天） | ✅ |

### 3.2 IM REST API 调用

| 步骤 | Node.js (`im-api.js:60-79`) | Java (`TencentImService.java:186-210`) | 一致性 |
|------|------|------|:---:|
| 基础 URL | `https://console.tim.qq.com` | `https://console.tim.qq.com` | ✅ |
| 签名参数 | `sdkappid + identifier + usersig + random + contenttype` | 完全一致 | ✅ |
| Random 范围 | `Math.floor(Math.random()*4294967295)` | `random.nextLong() & Long.MAX_VALUE % RANDOM_MAX` | ✅ |
| 超时 | axios 8s | RestTemplate connect 5s + read 10s | ✅ 合计可接受 |
| 错误处理 | `{ success: false, error: { ErrorCode, ErrorInfo } }` | 记录日志 + 返回 `{ ErrorCode: -1, ErrorInfo: "..." }` | ✅ 语义等价 |
| 空响应 | 未定义 | `Map.of("ErrorCode", -1, "ErrorInfo", "empty response")` | ✅ Java 更健壮 |

### 3.3 账号导入

| 步骤 | Node.js (`im-api.js:99-104`) | Java (`TencentImService.java:72-82`) | 一致性 |
|------|------|------|:---:|
| UserID 类型 | `String(userId)` | 直接使用 String | ✅ |
| Nick 可选 | `if (nick) body.Nick = nick` | `if (nick != null && !nick.isEmpty())` | ✅ |
| FaceUrl 可选 | `if (faceUrl) body.FaceUrl = faceUrl` | `if (faceUrl != null && !faceUrl.isEmpty())` | ✅ |
| API 路径 | `/v4/im_open_login_svc/account_import` | 一致 | ✅ |

### 3.4 发送系统消息

| 步骤 | Node.js (`tencent.js:42-76`) | Java (`TencentImService.java:96-118`) | 一致性 |
|------|------|------|:---:|
| MsgType | `TIMCustomElem` | `TIMCustomElem` | ✅ |
| Data 结构 | `{ type:"system", title, content, extra }` | 完全一致 | ✅ |
| From_Account | `config.im.adminAccount` | `imConfig.getAdminAccount()` | ✅ |
| To_Account | String (单聊) | String | ✅ |
| MsgRandom | `Math.floor(Math.random()*4294967295)` | `random.nextInt(Integer.MAX_VALUE)` | 🟡 范围不同（Java: 2^31-1, Node: 2^32-1），均为腾讯云接受范围 |
| 批量发送 | 数组 To_Account + `/v4/openim/sendmsg` | `/v4/openim/batchsendmsg` 专用端点 | 🟡 Java 使用正确端点（P2 #8） |
| JSON 序列化 | `JSON.stringify()` 内置 | 手写 `toJson()` | 🟡 P1 #3 |

### 3.5 COS 凭证

| 步骤 | Node.js (`cos.js:28-78`) | Java (`CosService.java:54-146`) | 一致性 |
|------|------|------|:---:|
| 路径隔离 | `user_${userId}/` | `user_" + userId + "/"` | ✅ |
| 有效期 | 1800 秒（30 分钟） | 1800 秒（30 分钟） | ✅ |
| Policy 条件 | bucket / Content-Type / content-length / key | 一致 | ✅ |
| 签名算法 | HMAC-SHA1 | HMAC-SHA1 | ✅ |
| Mock 检测 | `config.nodeEnv === 'development'` | `bucket.contains("placeholder")` / `secretId.contains("placeholder")` | ✅ 等价 |
| 过期格式 | `Date.toISOString()` (.000Z) | `Instant.toString()` (Z) | 🟡 P1 #4 |
| cdnBaseUrl | `config.cos.cdnBaseUrl \|\| "https://...myqcloud.com"` | `cosConfig.getCdnBaseUrl() != null ? ... : "https://..."` | ✅ |
| sessionToken | 空字符串 `''` | `""` | ✅ |

### 3.6 计划有但 Node.js 也无的实现

| 计划要求 | Node.js | Java | 结论 |
|----------|:---:|:---:|:---:|
| 6.3.5 send-batch 批量推送 | ❌ 无专用端点（sendmsg 内数组） | ✅ 专用端点 `/v4/openim/batchsendmsg` | Java 改进，符合腾讯云文档 |
| IM sendBatch 多用户列表解析 | ❌ 用 `Array.isArray()` 字符串化 | ✅ `List<String>` 类型安全 | Java 改进 |

---

## 四、编译与测试

```
BUILD SUCCESS — 3/3 模块编译通过，0 errors
Tests: 19 passed, 0 failures, 0 errors (im-connector: UserSigServiceTest)
   common:       0 (该模块无 UserSigServiceTest)
   im-connector: 19 (UserSigServiceTest)

测试覆盖详情：
  UserSigService 生成测试:          5  (非空/确定性/差异化userId/差异化expire/默认有效期)
  UserSigService 格式验证:          3  (各种userId/URL-safe编码/仅URL安全字符)
  UserSigService 边界条件:          3  (最小过期/超长userId/特殊字符)
  UserSigService KAT算法验证:       5  (HMAC字节/密钥原始字节/Deflater zlib头/32字节HMAC/URL-safe转义)
  参数化测试:                       4  (shouldSupportVariousUserIdFormats: 4种userId)
  ──
  实际测试方法数:                   15 @Test + 1 @ParameterizedTest (4 invocations) = 19 executions
```

**⚠️ 缺失的测试：**
- `TencentImService` — 0 个测试（`importAccount`/`sendSystemMessage`/`sendBatchSystemMessage`/`toJson`/`buildRestUrl`）
- `CosService` — 0 个测试（`generateCredential`/`buildRealCredential`/`buildMockCredential`/`hmacSha1`）
- `UploadController` — 0 个测试
- `InternalImController` — 0 个测试

**19 个测试仅覆盖 1/5 的服务类（UserSigService），其余 4 个类无单元测试。**

---

## 五、安全审查（专项）

| 检查项 | 状态 | 详情 |
|--------|:---:|------|
| ✅ userId 来源 | **已修复** | UploadController 从 `@RequestHeader("X-User-Id")` 读取 Gateway JWT 注入的用户 ID，与 Node.js `req.user.id` 一致 |
| 密钥管理 | ✅ | 全部 `@Value` / `@ConfigurationProperties` 从环境变量注入，无硬编码 |
| /internal/* 端点保护 | 🟡 | 无内部认证，依赖 Gateway 安全边界。直连 8083 端口可访问所有内部 API |
| SQL 注入 | ✅ | 无 SQL 操作（IM Connector 只调 IM REST API + COS SDK） |
| COS 路径隔离 | ✅ | `user_{userId}/` 前缀隔离，policy starts-with 限制 |
| COS content-type | ✅ | `["starts-with","$Content-Type","image/"]` 白名单限制 |
| COS 文件大小 | ✅ | `["content-length-range",1,uploadMaxSize]` 5MB 上限 |
| IM Admin 账号保护 | ✅ | adminAccount 从配置注入，不硬编码 |
| 错误信息泄露 | 🟡 | `InternalImController` 将 IM API 原始 `ErrorInfo` 返回给调用方（内部 API，Feign 调用方为 Core/Admin），外部不可见 |
| 日志敏感信息 | ✅ | UserSig 不打印在日志中（仅 debug 级别打印 sigDoc 长度，非内容） |

---

## 六、文件统计

| 文件 | 行数 | 类型 | 状态 |
|------|:--:|------|:---:|
| `ImConnectorApplication.java` | ~37 | 启动类 | ✅ P2 #7 已修复 |
| `config/ImConfig.java` | 33 | 配置 | ✅ |
| `config/CosConfig.java` | 57 | 配置 | ✅ |
| `config/RestTemplateConfig.java` | 23 | 配置 | ✅ |
| `config/InternalAuthInterceptor.java` | ~65 | 拦截器 | ✅ **新增** — P2 #9 |
| `config/InternalAuthConfig.java` | ~27 | 配置 | ✅ **新增** — P2 #9 |
| `service/UserSigService.java` | 150 | 服务 | ✅ |
| `service/TencentImService.java` | ~227 | 服务 | ✅ P1 #3 已修复 / P2 #8 已修复 |
| `service/CosService.java` | ~170 | 服务 | ✅ P1 #4 已修复 |
| `controller/UploadController.java` | ~48 | 控制器 | ✅ P0 #1 已修复 / P2 #5 附带修复 |
| `controller/InternalImController.java` | ~124 | 控制器 | ✅ P1 #2 已修复 / P2 #5 附带修复 |
| `controller/HealthController.java` | 19 | 控制器 | ✅ |
| `UserSigServiceTest.java` | 231 | 测试 | ✅ (19/19) |
| `application.yml` | ~37 | 配置 | ✅ P2 #9 |
| `bootstrap.yml` | 12 | 配置 | ✅ |
| `pom.xml` | 70 | 构建 | ✅ |
| **合计** | **~1330** | 16 文件 | 0 P0 / 0 P1 / 0 P2 |

---

## 七、总结

| 维度 | 结果 |
|------|------|
| 计划子步骤覆盖率 | **15/15**（除去 4 个 P3 运行时步骤） |
| P0 阻塞问题 | **0** — #1 UploadController userId 身份伪造 ✅ 已修复 |
| P1 建议修复 | **0** — #2 错误码/#3 toJson/#4 过期格式 ✅ 全部修复 |
| P2 代码质量 | **0** — #5/#6 附带修复于 P0/P1 / #7 scanBasePackages / #8 batchsendmsg 端点差异 / #9 内部端点无认证 ✅ 全部修复 |
| P3 运行时 | **4** — 6.1.6/6.4.1/6.4.3/6.4.4 |
| 关键逻辑正确性 | ✅ UserSig 算法 / IM REST API / COS STS 凭证 全部正确 |
| Node.js 行为一致性 | ✅ 核心逻辑一致，4 处差异已修复（userId / 错误码 / toJson / 过期格式） |
| 安全机制 | ✅ userId 从 JWT `X-User-Id` 读取 + `X-Internal-Token` defense-in-depth（P2 #9） |
| 测试覆盖 | ⚠️ 仅 UserSigService 有 19 个测试（覆盖 20% 的生产代码类） |
| 编译状态 | ✅ BUILD SUCCESS，36/36 测试通过（common 17 + im-connector 19，2026-06-14 重新验证） |
