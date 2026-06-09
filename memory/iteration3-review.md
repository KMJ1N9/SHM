---
name: iteration3-review
description: 第 3 轮「商品发布与浏览」审阅报告 — 6 后端修复 + 8 前端文件，全链路验证通过
metadata:
  type: project
  reviewDate: 2026-06-07
  iteration: 3
  totalFiles: 14
  fixed: 6
  remaining: 0
---

# 第 3 轮「商品发布与浏览」审阅报告

## 审阅范围

14 个文件的前后端全链路交叉审查：后端 6 修复 + 前端 8 新/改写。

## 后端修复（6 项）

| # | 级别 | 问题 | 文件 | 状态 |
|---|:--:|------|------|:--:|
| 1 | **P0** | `pool.execute()` 不支持参数化 LIMIT/OFFSET（mysql2 prepared stmt 协议限制） | [server/src/models/db.js](server/src/models/db.js) | ✅ 切换为 pool.query() |
| 2 | **P0** | list 响应未嵌套 seller 对象，缺少 cover_image | [server/src/services/product.js](server/src/services/product.js) | ✅ 添加 nestSeller() + extractCoverImage() |
| 3 | **P0** | detail 响应未嵌套 seller 对象 | [server/src/services/product.js](server/src/services/product.js) | ✅ |
| 4 | **P0** | COS 凭证未返回 policy，前端无法直传 | [server/src/utils/cos.js](server/src/utils/cos.js) | ✅ 返回 policy 字段 |
| 5 | **P1** | images 校验为 .optional()，API 规范要求必填 1-6 | [server/src/routes/product.js](server/src/routes/product.js) | ✅ 改为 .min(1).max(6).required() |
| 6 | **P1** | 发布/编辑未做敏感词过滤 + 编辑未检查 sold/frozen 状态 | [server/src/services/product.js](server/src/services/product.js) | ✅ 添加 DFA 过滤 + status 校验 |
| 7 | **P2** | findBySeller() 未解析 page/pageSize 为整数（query string → pool.query() 引号问题） | [server/src/repository/product.js](server/src/repository/product.js) | ✅ 整数解析 |

## 前端新建（8 项）

| # | 文件 | 操作 | 说明 |
|---|------|:--:|------|
| 1 | `miniprogram/src/api/product.js` | **重写** | 6 个 API：list/detail/create/update/remove/my/getCredential |
| 2 | `miniprogram/src/utils/cos.js` | **重写** | COS 直传：chooseImage → getCredential → uploadFile → URL |
| 3 | `miniprogram/src/components/ImageUploader.vue` | **重写** | 图片选择 + 缩略图网格 + 删除 + 预览 |
| 4 | `miniprogram/src/components/ProductCard.vue` | **重写** | 瀑布流卡片：封面/标题/价格/成色/地点 |
| 5 | `miniprogram/src/pages/product/publish.vue` | **重写** | 3 步表单：上传图片 → 填写信息 → 确认发布 |
| 6 | `miniprogram/src/pages/index/index.vue` | **重写** | 首页：搜索栏 + 分类标签 + 瀑布流 + 下拉刷新/触底加载 |
| 7 | `miniprogram/src/pages/product/detail.vue` | **重写** | 商品详情：图片轮播/价格/标签/描述/卖家信息/操作栏 |
| 8 | `miniprogram/src/pages/product/my.vue` | **重写** | 我的发布：状态 Tabs + 列表 + 删除 |

## 关键设计决策

1. **pool.execute() → pool.query()**：mysql2 的 execute() 使用服务端 PREPARE 协议，LIMIT/OFFSET 参数化占位符不被支持（mysql bug/限制）。query() 在客户端做参数化转义，功能等价且同样防 SQL 注入。

2. **Response 转换在 Service 层**：nestSeller() 和 extractCoverImage() 放在 Service 层（非 Repository），符合"Repository 只封装 SQL，Service 负责业务逻辑"的 5 层架构原则。

3. **ImageUploader 与 COS 上载分离**：ImageUploader 只做选择+预览（纯 UI 组件），实际上传由 publish.vue 调用 cos.js 的 chooseAndUpload() 完成。职责清晰。

4. **瀑布流使用双列 Flex**：不用 CSS columns（顺序问题），用 leftList/rightList 分别取奇偶索引，保持阅读顺序。

## 验证清单

| 检查项 | 维度 | 结果 |
|--------|------|:--:|
| GET /api/products 含 seller 嵌套 + cover_image | 后端 | ✅ |
| GET /api/products/:id 含 seller 嵌套 | 后端 | ✅ |
| POST /api/products 敏感词过滤 | 后端 | ✅ |
| POST /api/products 空 images 拦截 | 后端 | ✅ |
| GET /api/products?category= 分类筛选 | 后端 | ✅ |
| GET /api/products?priceMin=&priceMax= 价格筛选 | 后端 | ✅ |
| GET /api/products?sort=priceAsc 排序 | 后端 | ✅ |
| GET /api/products/my 我的发布 | 后端 | ✅ |
| GET /api/upload/cos-credential 返回 policy | 后端 | ✅ |
| 前端 uni-app 编译成功 | 构建 | ✅ |
| 禁止 SELECT * — 显式字段列表 | 数据库 | ✅ |
| 参数化查询防 SQL 注入 | 安全 | ✅ |
| API 统一返回 `{ code, message, data }` | 规范 | ✅ |
| 控制器无业务逻辑 | 架构 | ✅ |
| 所有 SCSS 使用 tokens.scss 变量 | 前端 | ✅ |
| Vue 3 Composition API + script setup | 前端 | ✅ |

## 第 3 轮审查新发现（2026-06-07 二次审查）

| # | 级别 | 问题 | 文件 | 状态 |
|---|:--:|------|------|:--:|
| BUG-007 | **P0** | chooseAndUpload() 内部调用 chooseImages() 重新打开相册，丢弃 ImageUploader 已选 tempFiles，用户需选两次图 | cos.js + publish.vue | ✅ 已修复 |
| BUG-008 | **P0** | detail.vue 重试按钮 @click="loadDetail" 传 event 对象为 id 参数，API 调用 /products/[object Object] | detail.vue | ✅ 已修复 |
| BUG-009 | **P1** | COS Policy content-type 条件使用数组精确匹配，格式非法 | server/src/utils/cos.js | ✅ 已修复 |
| BUG-010 | **P1** | x-cos-security-token 错误传 HMAC 签名值（永久密钥无需此字段） | cos.js (server + miniprogram) | ✅ 已修复 |
| BUG-011 | **P2** | db.js JSDoc 注释仍描述 pool.execute()，与实现不一致 | server/src/models/db.js | ✅ 已修复 |
| BUG-012 | **P2** | create()/update() 返回值未嵌套 seller，与 list/detail 格式不一致 | server/src/services/product.js | ✅ 已修复 |
| BUG-013 | **P2** | ProductCard.vue 占位图 data URI 无 SVG 内容，渲染为破损图标 | ProductCard.vue | ✅ 已修复 |

## 已知待办（非阻塞）

- 编辑商品页面（复用发布组件，预填已有数据）— 后续迭代补充
- "聊一聊" / "我想要" 按钮功能 — 迭代 5/6 实现
- Sass @import deprecation warnings — P2 已知

## 关联

- [[iteration2-review]] — 第 2 轮注册/登录
- [[project-state]] — 项目当前状态
- [[known-bugs]] — BUG-005: pool.execute() LIMIT/OFFSET 参数化失败
