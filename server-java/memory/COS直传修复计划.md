# COS 直传修复计划

## 问题

已填写真实 COS 凭证（SecretId/SecretKey/Bucket），但图片仍然上传到 `server/public/images/` 本地目录，腾讯云存储桶显示 0MB。

## 根因

两端的 COS 凭证生成逻辑都强制"开发环境走本地上传"，即使配置了真实凭证也会返回 `mock: true`，前端收到后走 `uploadToServer()` 而非直传 COS。

**Node.js** — `server/src/utils/cos.js:36-39`:
```js
const isMock =
    config.nodeEnv === 'development' ||   // ← 这行！开发环境强制 mock
    bucket.includes('placeholder') ||
    secretId.includes('placeholder');
```

**Java** — `CosService.java:78-83`:
```java
boolean isDev = Arrays.asList(env.getActiveProfiles()).contains("dev");
boolean isMock = isDev                    // ← 这行！dev profile 强制 mock
        || cosConfig.isMock()
        || bucket == null || bucket.contains("placeholder")
        || secretId == null || secretId.contains("placeholder")
        || secretKey == null || secretKey.isBlank();
```

## 修复方案

去掉开发环境的强制 mock 判断，仅凭凭证内容（是否含 `placeholder`）决定是否走 COS。

### Node.js 侧

**文件：** `server/src/utils/cos.js`

**改前：**
```js
const isMock =
    config.nodeEnv === 'development' ||
    bucket.includes('placeholder') ||
    secretId.includes('placeholder');
```

**改后：**
```js
const isMock =
    bucket.includes('placeholder') ||
    secretId.includes('placeholder');
```

### Java 侧

**文件：** `server-java/im-connector/src/main/java/com/shm/im/service/CosService.java`

**改前：**
```java
boolean isDev = Arrays.asList(env.getActiveProfiles()).contains("dev");
boolean isMock = isDev
        || cosConfig.isMock()
        || bucket == null || bucket.contains("placeholder")
        || secretId == null || secretId.contains("placeholder")
        || secretKey == null || secretKey.isBlank();
```

**改后：**
```java
boolean isMock = cosConfig.isMock()
        || bucket == null || bucket.contains("placeholder")
        || secretId == null || secretId.contains("placeholder")
        || secretKey == null || secretKey.isBlank();
```

同时删除不再使用的：
- `import java.util.Arrays;`
- `import org.springframework.core.env.Environment;`
- 字段 `private final Environment env;`
- 构造器参数 `Environment env`

## 影响

- 修复后，开发环境 + 真实凭证 = 直传 COS
- 开发环境 + placeholder 凭证 = 仍然走本地（开发调试不用改配置）
- 生产环境行为不变

## 注意

- 之前已上传到 `server/public/images/` 的图片不会自动迁移到 COS
- 修复后**新上传**的图片才会到 COS 存储桶
- 前端 `cos.js` 无需改动，它根据后端返回的 `mock` 字段自动选择上传路径

## 实施时机

待定。当前开发调试阶段走本地上传更方便（不消耗 CDN 流量）。
