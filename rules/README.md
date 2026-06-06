# SHM 项目开发规则总览

`rules/` 目录下有 14 份规范文件，覆盖完整开发链路。所有代码生成与修改必须遵守这些约束。

## 规则文件清单及核心约束

### 1. ai-behavior-rules.md — 行为约束
- 核心原则：先阅读相关文件→理解现有架构→优先复用→保持风格一致→最小化修改→避免影响已有功能
- 禁止：擅自重构/升级依赖/修改数据库结构/修改 API 格式/修改核心逻辑/移动文件/重命名模块/引入新框架/创建重复功能/写临时方案/留 TODO 不实现/生成伪代码
- 职责：辅助开发，非主导架构决策

### 2. ai-output-rules.md — 输出规范
- 先分析已有代码→优先复用→保持风格一致→最小化改动
- 输出完整可运行代码，不生成伪代码，不省略关键实现

### 3. coding-standards.md — 编码规范
- 命名：camelCase（变量）、PascalCase（类）、UPPER_CASE（常量）、kebab-case（前端文件）
- TypeScript：strict mode 必须开启、禁止 `any`、优先 `interface` 而非 `type`、明确返回值类型
- 工程原则：SOLID、DRY、KISS、Separation of Concerns、Single Responsibility、High Cohesion、Low Coupling

### 4. database-rules.md — 数据库规范
- 命名：snake_case（表名和字段名）
- 所有表必须包含：`id`、`created_at`、`updated_at`
- 禁止：`SELECT *`、无索引查询、N+1 查询、循环内频繁查询
- 推荐：明确查询字段、用 JOIN 替代循环、添加适当索引、使用分页

### 5. api-rules.md — API 规范
- RESTful 风格：`GET /users`、`GET /users/:id`、`POST /users`、`PUT /users/:id`、`DELETE /users/:id`
- 统一返回格式：`{ "code": 0, "message": "success", "data": {} }`
- 禁止返回格式不统一的响应

### 6. security-rules.md — 安全规范
- 必须：校验所有用户输入、参数化查询、敏感信息加密、权限校验、JWT 校验、防 XSS、防 CSRF、防 SQL 注入
- 禁止：硬编码密钥、日志输出密码、明文存储敏感信息

### 7. error-handling-rules.md — 错误处理规范
- 禁止空 catch 块 `catch (e) {}`
- 必须：输出错误日志、返回统一错误格式、保持错误可追踪
- 日志：使用统一日志系统、输出错误上下文、输出 traceId
- 禁止：`console.log` 到处输出、打印敏感数据、无意义日志

### 8. file-rules.md — 文件规范
- 单文件不超过 500 行，超过必须拆分

### 9. performance-rules.md — 性能规范
- 必须：减少重复渲染、避免重复请求、合理缓存、懒加载大型模块、控制包体积
- 禁止：无意义 `useEffect`、死循环请求、巨型组件、巨型函数

### 10. test-rules.md — 测试规范
- 提交前必须：lint 通过、build 成功、单元测试通过
- 新增业务逻辑必须包含测试

### 11. function-rules.md — 函数规范
- 单函数：不超过 80 行、单一职责、参数不超过 5 个
- 复杂逻辑必须拆分

### 12. git-rules.md — Git 提交规范
- 格式：`feat:` / `fix:` / `refactor:` / `docs:` / `test:` / `style:`
- 示例：`feat: 添加用户登录功能` / `fix: 修复 JWT 过期问题`

### 13. comment-rules.md — 注释规范
- 必须解释：为什么这样做、业务背景、边界条件
- 禁止废话注释（如 `// 定义用户变量 const user = {}`）

### 14. ui-rules.md — UI 规范
- 必须：统一设计风格、控制颜色数量、间距统一、响应式布局、适配移动端
- 禁止：随意使用颜色、弹窗套弹窗、内联样式泛滥
