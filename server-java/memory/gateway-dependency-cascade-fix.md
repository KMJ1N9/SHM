# Gateway 依赖版本级联冲突修复

**日期：** 2026-06-13  
**状态：** ✅ 已修复，已验证  
**影响范围：** `server-java/pom.xml`（父 POM，1 个文件）

---

## 问题

Gateway 启动后访问 `http://localhost:8080/api/health` 返回 `ERR_EMPTY_RESPONSE`，浏览器无任何响应。

---

## 根本原因

Spring Cloud Gateway 4.1.6（来自 Spring Cloud 2023.0.5）编译时用了比 Spring Boot 3.2.7 更新的依赖，导致两级 `NoSuchMethodError` 级联：

```
Spring Cloud Gateway 4.1.6
  └─ ForwardedHeadersFilter → 调用 HttpHeaders.headerSet()
       └─ 不存在于 Spring Framework 6.1.10 ❌ → 需要 6.2.x ✓
            └─ 升级到 6.2.5 后 → ReactorUriHelper → 调用 HttpServerRequest.forwardedPrefix()
                 └─ 不存在于 reactor-netty 1.1.20 ❌ → 需要 1.1.23 ✓
```

---

## 修复内容

### 修改 1：覆盖 Spring Framework 版本

```xml
<properties>
    <!-- 覆盖 Spring Framework 版本：Spring Cloud Gateway 4.1.6 需要 6.2.x 的 HttpHeaders.headerSet() -->
    <spring-framework.version>6.2.5</spring-framework.version>
</properties>
```

### 修改 2：添加 reactor-netty 版本属性

```xml
<properties>
    <!-- 覆盖 reactor-netty：Spring Framework 6.2.5 需要 1.1.23+ 的 forwardedPrefix() -->
    <reactor-netty.version>1.1.23</reactor-netty.version>
</properties>
```

### 修改 3：reactor-netty 依赖管理覆盖

```xml
<dependencyManagement>
    <dependencies>
        <!-- Spring Framework BOM（必须在 spring-boot-dependencies 之前） -->
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-framework-bom</artifactId>
            <version>6.2.5</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- Spring Boot BOM -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- reactor-netty 版本覆盖 -->
        <dependency>
            <groupId>io.projectreactor.netty</groupId>
            <artifactId>reactor-netty-http</artifactId>
            <version>${reactor-netty.version}</version>
        </dependency>
        <dependency>
            <groupId>io.projectreactor.netty</groupId>
            <artifactId>reactor-netty-core</artifactId>
            <version>${reactor-netty.version}</version>
        </dependency>
        ...
    </dependencies>
</dependencyManagement>
```

---

## 版本对照表

| 组件 | Spring Boot 3.2.7 管理 | 实际需要 | 新增方法 |
|:--|:--|:--|:--|
| Spring Framework | 6.1.10 | **6.2.5** | `HttpHeaders.headerSet()` |
| reactor-netty | 1.1.20 | **1.1.23** | `HttpServerRequest.forwardedPrefix()` |

---

## 踩坑记录

| # | 坑 | 教训 |
|:--|:--|:--|
| 1 | reactor-netty 1.1.22 没有 `forwardedPrefix()` | 经实测，1.1.23 才引入此方法 |
| 2 | 阿里云 Maven 镜像 1.1.22 下载为 0 字节 | 但 1.1.23 正常（481 KB），备用 Maven Central |
| 3 | IDEA 本地仓库路径 = `D:\MAVEN\local` ≠ Maven CLI 默认 `C:\Users\6\.m2\repository` | 需手动复制 jar 到 IDEA 路径 |
| 4 | `<spring-framework.version>` property 单独设置不生效 | 必须配合 `spring-framework-bom` BOM 导入，且放在 `spring-boot-dependencies` 之前 |

---

## 验证

```bash
cd server-java
mvn clean compile -DskipTests  # 全部 6 模块 BUILD SUCCESS
```

```json
GET http://localhost:8080/api/health
→ {"code":0,"message":"ok","data":{"status":"ok","timestamp":1781365759110}}
```
