# GitHub Copilot Instructions — 校园二手交易小程序

## 项目上下文

广州应用科技学院肇庆校区 C2C 二手交易平台微信小程序。
技术栈：uni-app (Vue 3) + Pinia + Vite + Sass/SCSS，目标平台 mp-weixin。

## 编码规则（始终遵守）

### 命名规范
- 变量/函数：camelCase（`userName`, `getOrderList`）
- 类/组件：PascalCase（`UserService`, `ProductCard`）
- 常量：UPPER_CASE（`MAX_PAGE_SIZE`）
- 前端文件：kebab-case（`product-card.vue`）
- 数据库表/字段：snake_case（`user_info`, `created_at`）

### TypeScript 规范
- strict mode，禁止 `any`
- 优先使用 `interface` 而非 `type`
- 函数必须明确返回值类型

### 函数与文件限制
- 单函数 ≤ 80 行
- 函数参数 ≤ 5 个
- 单文件 ≤ 500 行

### API 调用规范
- RESTful 风格，资源名复数
- 统一返回格式：`{ code: 0, message: "success", data: {} }`
- 错误响应同样走此格式（code 非 0）

### UI 规范
- 使用 `@/styles/tokens.scss` 中的设计 Token 变量
- 主色调：#4A90D9（蓝色），不随意引入新颜色
- 间距遵循 8pt 系统，rpx 单位（1px = 2rpx，375px 基准）
- 禁止内联样式泛滥，优先 class + scss

### 安全规范
- 所有用户输入必须校验
- 数据库查询使用参数化（禁止字符串拼接 SQL）
- 禁止硬编码密钥、密码、Token
- catch 块必须有错误处理，禁止空 catch

### 注释规范
- 注释解释"为什么这样做"，不是"做了什么"
- 标注业务背景和边界条件
- 不写废话注释（如 `// 定义用户变量`）

## 代码补全规则

1. 补全前先检查是否与现有代码重复 —— 优先复用已有模块
2. 补全代码必须完整可运行，禁止伪代码或省略实现
3. 保持与当前文件风格一致（缩进、引号、分号等）
4. 使用 `@/` 路径别名引用项目内模块（与 tsconfig.json paths 一致）

## 禁止行为

- 输出 `any` 类型
- 输出空 catch 块
- 输出 `SELECT *` SQL 语句
- 输出内联 style（除非动态计算值）
- 输出超过 500 行的单文件
- 引入新依赖（除非明确需要）
- 重构与当前改动无关的代码
