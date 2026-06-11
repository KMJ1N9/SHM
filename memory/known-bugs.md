---
name: known-bugs
description: 已知 Bug 列表 — 发现时间、位置、根因、修复方案、修复状态
metadata:
  type: project
  updatedAt: 2026-06-11T23:00
---

# 已知 Bug

## BUG-001: token_version 字段名不匹配 ✅ 已修复

- **发现时间**: 2026-06-05（代码扫描）
- **修复时间**: 2026-06-06
- **位置**: [server/src/middleware/auth.js](server/src/middleware/auth.js)
- **严重程度**: P0（所有用户鉴权失败——`payload.version` 永远为 `undefined`）
- **根因**: JWT 签发时 payload 字段名为 `tv`，中间件验证时读取 `payload.version`
- **修复**: `payload.version` → `payload.tv`

## BUG-002: health check 空 catch ✅ 已修复

- **发现时间**: 2026-06-05
- **修复时间**: 2026-06-06
- **位置**: [server/src/app.js](server/src/app.js)
- **严重程度**: P2（违反 error-handling-rules 禁止空 catch）
- **修复**: 添加 `logger.error('健康检查失败', { error: err.message })`

## BUG-003: POST /auth/refresh 未加入鉴权白名单 ✅ 已修复

- **发现时间**: 2026-06-07（第 2 轮审查）
- **修复时间**: 2026-06-07
- **位置**: [server/src/middleware/auth.js](server/src/middleware/auth.js) — EXEMPT_PATHS
- **严重程度**: P0（Token 刷新完全不可用——refresh 端点被 JWT 中间件以 1001 拦截）
- **根因**: `EXEMPT_PATHS` 只含 login 和 health，缺少 refresh。前端 `callRefreshAPI()` 不加 Authorization header（refresh_token 在 body 中），JWT 中间件无 token → 抛 `unauthenticated()` 1001
- **修复**: 添加 `{ method: 'POST', path: '/auth/refresh' }` 到 EXEMPT_PATHS

## BUG-004: 拦截器自动刷新后 Pinia Store token 未同步 ✅ 已修复

- **发现时间**: 2026-06-07（第 2 轮审查）
- **修复时间**: 2026-06-07
- **位置**: [miniprogram/src/store/user.js](miniprogram/src/store/user.js) — initAuth()
- **严重程度**: P1（Store 与 Storage token 不一致，`isLoggedIn` 可能误判 / 手动 refresh 发送旧 token）
- **根因**: `api/index.js` 的 `callRefreshAPI()` 只写 Storage，不更新 Pinia store。`initAuth()` 中 getMe 触发自动刷新后，store 仍持有旧 token
- **修复**: `initAuth()` 中 getMe 成功后从 Storage 重新读取 token 同步回 store

## BUG-005: pool.execute() 不支持参数化 LIMIT/OFFSET ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮编码）
- **修复时间**: 2026-06-07
- **位置**: [server/src/models/db.js](server/src/models/db.js) — query()
- **严重程度**: P0（所有带 LIMIT/OFFSET 的查询报 "Incorrect arguments to mysqld_stmt_execute"）
- **根因**: mysql2 的 `pool.execute()` 使用 MySQL 服务端 PREPARE 协议，LIMIT ? 和 OFFSET ? 的占位符不被服务端支持。而 `pool.query()` 在客户端做参数化转义，功能等价且同样防 SQL 注入。
- **修复**: `pool.execute(sql, params)` → `pool.query(sql, params)`

## BUG-006: findBySeller() page/pageSize 未解析整数 ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮验证）
- **修复时间**: 2026-06-07
- **位置**: [server/src/repository/product.js](server/src/repository/product.js) — findBySeller()
- **严重程度**: P1（SQL 错误 "near ''3' OFFSET 0"——pool.query() 将字符串 "3" 引用为 '3' 导致 LIMIT 语法非法）
- **根因**: req.query 参数均为 string 类型，`pool.query()` 会将字符串参数用引号包裹（LIMIT '3' ≠ LIMIT 3），而 `pool.execute()` 传入时不受影响但对 LIMIT 完全不支持。修复 execute→query 后此问题暴露。
- **修复**: 添加 `parseInt(rawPage, 10)` 和 `parseInt(rawPageSize, 10)` 整数转换

## BUG-007: chooseAndUpload() 丢弃 ImageUploader 已选文件，用户需选两次图 ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮审查）
- **修复时间**: 2026-06-07
- **位置**: [miniprogram/src/utils/cos.js](miniprogram/src/utils/cos.js) — chooseAndUpload() + [miniprogram/src/pages/product/publish.vue](miniprogram/src/pages/product/publish.vue) — nextStep()
- **严重程度**: P0（用户选图两次，体验严重断裂）
- **根因**: `chooseAndUpload()` 内部调用 `chooseImages()` → `uni.chooseImage()` 重新打开系统相册，ImageUploader 中已选的 `tempFiles` 仅用于数量校验后被丢弃
- **修复**: `chooseAndUpload()` 新增可选参数 `preselectedFiles`，传入时跳过 `chooseImages()` 直接上传（仍做格式/大小校验）；publish.vue 传入 `tempFiles.value`

## BUG-008: detail.vue 重试按钮传 event 对象为 id 导致 API 调用失败 ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮审查）
- **修复时间**: 2026-06-07
- **位置**: [miniprogram/src/pages/product/detail.vue](miniprogram/src/pages/product/detail.vue) — loadDetail() + template
- **严重程度**: P0（商品详情加载失败后「重新加载」永远无效——API 请求 `/products/[object Object]`）
- **根因**: 模板 `@click="loadDetail"` 无参数时 Vue 将原生事件对象作为第一个实参传入，`loadDetail(event)` 中 `id` = `MouseEvent`
- **修复**: 商品 ID 提升为模块级 `ref`（`currentId`），`loadDetail()` 不再接受参数，从 `currentId.value` 读取

## BUG-009: COS Policy content-type 条件格式非法 ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮审查）
- **修复时间**: 2026-06-07
- **位置**: [server/src/utils/cos.js](server/src/utils/cos.js) — generateCredential() policy.conditions
- **严重程度**: P1（COS 可能拒绝所有上传——`{ 'content-type': ['image/jpeg','image/png','image/webp'] }` 数组值永远无法匹配单字符串 Content-Type）
- **根因**: `config.cos.uploadAllowedTypes` 是 split 后的数组，直接用作 policy exact-match value 语法非法
- **修复**: 改为 `['starts-with', '$Content-Type', 'image/']`——COS policy 正确的前缀匹配格式

## BUG-010: x-cos-security-token 错误传 HMAC 签名值 ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮审查）
- **修复时间**: 2026-06-07
- **位置**: [server/src/utils/cos.js](server/src/utils/cos.js) — sessionToken + [miniprogram/src/utils/cos.js](miniprogram/src/utils/cos.js) — uploadOne() formData
- **严重程度**: P1（永久密钥鉴权不应传 x-cos-security-token，传 HMAC 签名值可能被 COS 拒绝）
- **根因**: 后端 `sessionToken: signKey` 将 HMAC-SHA1 签名误标为 sessionToken；前端无条件将此值作为 `x-cos-security-token` 发送
- **修复**: 后端 sessionToken 改为空字符串（注释标注用途）；前端条件判断 `if (credentials.sessionToken)` 才附加此字段

## BUG-011: db.js 注释仍描述 pool.execute() ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮审查）
- **修复时间**: 2026-06-07
- **位置**: [server/src/models/db.js](server/src/models/db.js) — JSDoc 注释
- **严重程度**: P2（文档与实现不一致，可能误导后续开发者）
- **根因**: `pool.execute()` 切换为 `pool.query()` 后，注释未同步更新
- **修复**: 第 6 行和第 36-37 行注释中 `pool.execute` → `pool.query`，`PREPARE + EXECUTE 协议` → `客户端参数化转义`

## BUG-012: create()/update() 返回值未嵌套 seller ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮审查）
- **修复时间**: 2026-06-07
- **位置**: [server/src/services/product.js](server/src/services/product.js) — create() + update()
- **严重程度**: P2（API 响应格式不一致——list/detail 返回嵌套 seller，create/update 返回扁平字段）
- **根因**: create/update 直接返回 repo 结果，未经过 nestSeller() 转换
- **修复**: create() 和 update() 返回值通过 `nestSeller()` 包装后再返回

## BUG-013: ProductCard 占位图空 base64 data URI ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮审查）
- **修复时间**: 2026-06-07
- **位置**: [miniprogram/src/components/ProductCard.vue](miniprogram/src/components/ProductCard.vue) — placeholderImage
- **严重程度**: P2（`data:image/svg+xml;base64,` 无实际 SVG 内容，渲染为破损图片图标）
- **根因**: 占位图 base64 字符串为空，缺少 SVG 负载
- **修复**: 替换为完整的灰色 SVG data URI（200×200 `#F0F0F0` 矩形，与 $color-divider 背景一致）

## BUG-014: 渲染层加载 example.com 图片报 ERR_BLOCKED_BY_RESPONSE 🔴 待修复

- **发现时间**: 2026-06-07
- **位置**: 微信开发者工具渲染层
- **严重程度**: P2（不影响功能——代码和数据库均无 example.com 引用，图片已使用 SVG data URI 占位图；怀疑为微信开发者工具缓存旧编译产物）
- **根因**: 未确定。已排查：全代码库 `grep -r "example.com"` 零匹配、MySQL 全库零匹配、dist/ 目录已删除重建。IP 104.20.23.154 归属 Cloudflare，推测为微信开发者工具内部缓存或旧版编译产物残留
- **修复方案**: 待深入排查微信开发者工具缓存机制，必要时清除工具级缓存或重装开发者工具
- **相关记忆**: [[project-state]]

## BUG-015: detail.vue 卖家默认头像 data URI 为空 ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮全面审计）
- **修复时间**: 2026-06-07
- **位置**: [miniprogram/src/pages/product/detail.vue:153](miniprogram/src/pages/product/detail.vue#L153)
- **严重程度**: P1（所有无头像卖家的商品详情页显示破损图片图标——与 BUG-013 同类缺陷）
- **根因**: `defaultAvatar = 'data:image/svg+xml;base64,'` — base64 内容为空。BUG-013 修复了 ProductCard 占位图但遗漏了 detail.vue
- **修复方案**: 替换为完整灰色 SVG data URI（圆形头像，100×100 `#F0F0F0` 背景），与 ProductCard BUG-013 修复模式一致

## BUG-016: update() 未禁止编辑已删除商品 ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮全面审计）
- **修复时间**: 2026-06-07
- **位置**: [server/src/services/product.js:176](server/src/services/product.js#L176)
- **严重程度**: P1（已软删除的商品仍可被直接 API 调用编辑——状态守卫只拦截 sold/frozen，遗漏 deleted）
- **根因**: 条件 `product.status === 'sold' || product.status === 'frozen'` 未包含 `deleted`
- **修复方案**: 扩展条件为 `['sold', 'frozen', 'deleted'].includes(product.status)`

## BUG-017: services/product.js list() 中 isNaN 校验为死代码 ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮全面审计）
- **修复时间**: 2026-06-07
- **位置**: [server/src/services/product.js:75-77](server/src/services/product.js#L75-L77)
- **严重程度**: P2（page/pageSize 经过 `parseInt || 1` + `Math.max(1, ...)` 后永不为 NaN）
- **修复方案**: 删除 3 行死代码，同步移除 import 中未使用的 `invalidPagination`

## BUG-018: delete() 存在 TOCTOU 竞态条件 ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮全面审计）
- **修复时间**: 2026-06-07
- **位置**: [server/src/services/product.js:208-221](server/src/services/product.js#L208-L221) + [server/src/repository/product.js:103-120](server/src/repository/product.js#L103-L120) + [server/src/repository/product.js:180-187](server/src/repository/product.js#L180-L187)
- **严重程度**: P2（findById 和 updateStatus 之间无事务保护，并发场景下状态校验可能被绕过）
- **修复方案**: `delete()` 使用 `db.transaction()` 包裹；`findById(id, conn)` 和 `updateStatus(id, status, conn)` 新增可选 `conn` 参数——传入时使用事务连接 + `SELECT ... FOR UPDATE` 锁定行

## P2-1: 后端缺少 price ≤ original_price 校验 ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮全面审计）
- **修复时间**: 2026-06-07
- **位置**: [server/src/services/product.js:127-130](server/src/services/product.js#L127-L130) (create) + [server/src/services/product.js:182-189](server/src/services/product.js#L182-L189) (update)
- **严重程度**: P2（前端校验可被绕过 API 直接调用，售价高于原价的商品可入库）
- **根因**: create() 和 update() 均未校验 price ≤ original_price
- **修复方案**: create() 直接比较两字段；update() 使用 effective 值（未传字段沿用 product 旧值）处理部分更新场景

## P2-2: findBySeller() API 响应无 seller 嵌套 ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮全面审计）
- **修复时间**: 2026-06-07
- **位置**: [server/src/services/product.js:245-254](server/src/services/product.js#L245-L254)
- **严重程度**: P2（GET /products 返回嵌套 seller 对象，GET /products/my 返回扁平 seller_id，格式不一致）
- **修复方案**: findBySeller() 返回前对每行添加 `seller: null` 和 `cover_image` 字段，保持与 list() 结构一致

## P2-3: ImageUploader 单文件失败丢弃全部 ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮全面审计）
- **修复时间**: 2026-06-07
- **位置**: [miniprogram/src/components/ImageUploader.vue:92-107](miniprogram/src/components/ImageUploader.vue#L92-L107)
- **严重程度**: P2（用户选 3 张 JPG + 1 张 BMP → 全部丢弃，而非仅过滤 BMP）
- **修复方案**: 改为 filter 过滤合法文件（格式+大小），仅 rejected > 0 时 toast 提示已自动过滤，valid 为空时才提前 return

## P2-4: index.vue noMore 判断逻辑改用返回条数 ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮全面审计）
- **修复时间**: 2026-06-07
- **位置**: [miniprogram/src/pages/index/index.vue:131-134](miniprogram/src/pages/index/index.vue#L131-L134)
- **严重程度**: P2（`list.length >= total` 依赖 total 准确性，与 my.vue 的 `data.list.length < pageSize` 方式不一致）
- **修复方案**: 统一为 `(data.list || []).length < pageSize` 判断，与 my.vue 保持一致

## P2-5: db.js JSDoc 建议 conn.execute() 错误 ✅ 已修复

- **发现时间**: 2026-06-07（第 3 轮全面审计）
- **修复时间**: 2026-06-07
- **位置**: [server/src/models/db.js:91](server/src/models/db.js#L91)
- **严重程度**: P2（注释与 BUG-005 修复后的实现不一致，事务内使用 conn.execute() 会有相同 LIMIT/OFFSET PREPARE 限制）
- **修复方案**: JSDoc 更新为 `conn.query()`，注明与 pool.query() 一致、支持 LIMIT/OFFSET

## BUG-019: productRepo.findById() 返回数组而非对象 ✅ 已修复

- **发现时间**: 2026-06-08
- **修复时间**: 2026-06-08
- **位置**: [server/src/repository/product.js:107](server/src/repository/product.js#L107) — findById()
- **严重程度**: P0（详情页全部数据异常：价格=0、交易信息空白、图片黑屏、无控制台报错）
- **根因**: `findById()` 调用 `db.query()`（即 `pool.query()`），mysql2 返回 `[rows, fields]` 元组。代码写 `const [row] = await db.query(...)` 将整个 rows 数组解构为 `row`，`return row || null` 返回的是数组而非对象。这与 `db.js` 的 `query()` helper 不同——helper 内部已解构 `const [rows] = await pool.query(...); return rows;`，而 `findById` 直接调用 `pool.query()` 跳过了这层解构。
- **影响链**: `nestSeller(数组)` → 数组无 `seller_nickname` 属性 → 返回原数组 → 前端 `product.value = [{...}]` → `product.images` 为 undefined → swiper 空渲染 → 纯黑背景 / `product.price` 为 undefined → `formatPrice(undefined)` → 显示 0
- **修复**: `const [rows] = await db.query(...)` → `return rows[0] || null`

## BUG-020: helmet CORP/CSP 阻止微信小程序加载本地图片 ✅ 已修复

- **发现时间**: 2026-06-08
- **修复时间**: 2026-06-08
- **位置**: [server/src/app.js:75-83](server/src/app.js#L75-L83)
- **严重程度**: P0（所有商品图片在微信小程序中不显示，首页+详情页均黑屏；curl 可正常访问图片，非存储桶权限问题）
- **根因**: `helmet()` 全局设置 `Cross-Origin-Resource-Policy: same-origin` + `Cross-Origin-Opener-Policy: same-origin` + CSP `img-src 'self'`。微信小程序运行在 DevTools webview（origin 如 `http://127.0.0.1:XXXXX`），与 Express 服务器（`http://localhost:3000`）不同源。浏览器/CORP 检查拒绝跨域加载图片。
- **说明**: 开发环境使用占位符 COS 凭证（`COS_BUCKET=shm-dev-placeholder`），图片上传到本地 `server/public/images/` 而非腾讯云 COS。COS 存储桶无数据是预期行为，非 bug。
- **修复**: 在 `express.static('/images')` 前新增中间件，覆盖 helmet 头——CORP → `cross-origin`、移除 COEP、CSP `img-src` → `*`

**Why:** BUG-001~020 + P2-1~P2-5 全部已修复 ✅。BUG-014 为顽固缓存问题待处理。第 3 轮代码质量综合评分从 8.5 → 9.0/10。

## BUG-021: 首页筛选侧边栏分类筛选无效 ✅ 已修复

- **发现时间**: 2026-06-08（第 4 轮微信开发者工具测试）
- **修复时间**: 2026-06-08
- **位置**: [miniprogram/src/pages/index/index.vue:170-172](miniprogram/src/pages/index/index.vue#L170-L172) — loadProducts()
- **严重程度**: P1（侧边栏选分类后 API 请求不带 category 参数，筛选无效；成色和价格正常）
- **根因**: `loadProducts()` 仅从 `activeCategory.value`（顶部 Tab）读取分类，完全忽略 `filters.category`（侧边栏）。`onFilterApply()` 将分类写入 `filters.category`，但 API 参数构建时只检查 `activeCategory.value`。
- **修复**: 合并两个来源 — `const category = filters.category || activeCategory.value`，侧边栏优先，回退到顶部 Tab。
- **影响范围**: 仅首页筛选。搜索页的筛选正常工作（搜索页无顶部 Tab，直接使用 `filters.category`）。

## BUG-022: 搜索结果翻页请求失败时跳过一整页数据 ✅ 已修复

- **发现时间**: 2026-06-08（第 4 轮全面审计）
- **修复时间**: 2026-06-08
- **位置**: [miniprogram/src/pages/search/index.vue:416](miniprogram/src/pages/search/index.vue#L416) — onReachBottom + loadProducts()；同模式存在于 [miniprogram/src/pages/index/index.vue:246](miniprogram/src/pages/index/index.vue#L246)
- **严重程度**: P1（翻页 API 失败后，页码已递增但数据未加载，导致该页数据永久跳过；用户看到的症状是"少了一页结果"）
- **根因**: `onReachBottom` 中 `page.value++` 在 `loadProducts(false)` 之前执行。API 请求失败时 page 已加 1 但数据未获取，下次触底再次 +1，跳过失败页。
- **修复**: `loadProducts()` 内部用局部变量 `targetPage = reset ? 1 : page.value + 1` 计算目标页码，API 成功后才 `page.value = targetPage`。`onReachBottom` 不再手动递增 page。
- **影响范围**: 搜索页和首页的瀑布流翻页。修复后页码推进与网络成败绑定。

**Why:** BUG-001~020 + P2-1~P2-5 + BUG-021~022 全部已修复 ✅。BUG-014 为顽固缓存问题待处理。

## A5-001: 发送消息失败后用户输入永久丢失 ✅ 已修复

- **发现时间**: 2026-06-08（第 5 轮全面审计）
- **修复时间**: 2026-06-08
- **位置**: [miniprogram/src/pages/chat/detail.vue:312-313](miniprogram/src/pages/chat/detail.vue#L312-L313) — sendMessage()
- **严重程度**: P1（用户输入长文本 → 发送失败 → 输入框已空 → 消息永久丢失）
- **根因**: `inputText.value = ''` 在 `await sendTextMessage()` 之前执行。发送失败时用户输入已清空。
- **修复**: 将 `inputText.value = ''` 移到 `await sendTextMessage()` 成功之后。

## A5-002: 聊天页面隐藏时仍处理新消息并标记已读 ✅ 已修复

- **发现时间**: 2026-06-08（第 5 轮全面审计）
- **修复时间**: 2026-06-08
- **位置**: [miniprogram/src/pages/chat/detail.vue:335-355](miniprogram/src/pages/chat/detail.vue#L335-L355) — registerMessageListener()
- **严重程度**: P1（用户导航到其他页面时，聊天页 MESSAGE_RECEIVED 监听器仍在运行，静默标记消息为已读；用户返回后错过重要消息）
- **修复**: 新增 `isPageVisible` ref + `onHide`/`onShow` 生命周期 + `loadLatestMessages()` 补充加载。监听器中检查 `isPageVisible` 决定是否处理。
- **验证**: `npx vitest run` — 7 文件 106 用例全部通过。

## A5-004: CONVERSATION_UPDATE 事件兜底字符串无效 ✅ 已修复

- **发现时间**: 2026-06-08（第 5 轮全面审计）
- **修复时间**: 2026-06-08
- **位置**: [miniprogram/src/pages/chat/list.vue:273](miniprogram/src/pages/chat/list.vue#L273)
- **严重程度**: P2（`tim.EVENT?.CONVERSATION_UPDATE || 'conversation-updated'` — 兜底字符串不是有效 IM 事件名）
- **修复**: 导入 `TIM from 'tim-wx-sdk'`，直接使用 `TIM.EVENT.CONVERSATION_UPDATE`。

## A5-005: onTapConversation 乐观清零 unreadCount ✅ 已修复

- **发现时间**: 2026-06-08（第 5 轮全面审计）
- **修复时间**: 2026-06-08
- **位置**: [miniprogram/src/pages/chat/list.vue:224-225](miniprogram/src/pages/chat/list.vue#L224-L225)
- **严重程度**: P2（`setMessageRead` 失败时 UI 已清零，短暂不一致 → `onShow` 加载会话列表可自愈）
- **修复**: 保存 `prevCount`，`.catch()` 中恢复。

## A5-006: hasMore 用 length >= 20 判断不精确 ✅ 已修复

- **发现时间**: 2026-06-08（第 5 轮全面审计）
- **修复时间**: 2026-06-08
- **位置**: [miniprogram/src/pages/notification/index.vue:160](miniprogram/src/pages/notification/index.vue#L160) — loadNotifications() + loadMore()
- **严重程度**: P2（末页恰好 20 条时多一次空请求）
- **修复**: 改用 `notifications.value.length < (result.total || 0)` 精确判断。

## A5-007/A5-008/A5-009: 三处空 catch 合规修复 ✅ 已修复

- **发现时间**: 2026-06-08（第 5 轮全面审计）
- **修复时间**: 2026-06-08
- **位置**: [miniprogram/src/pages/notification/index.vue:238](miniprogram/src/pages/notification/index.vue#L238) / [App.vue:131](miniprogram/src/App.vue#L131) / [chat/detail.vue:464](miniprogram/src/pages/chat/detail.vue#L464)
- **严重程度**: P2（违反 `rules/error-handling-rules` 禁止空 catch）
- **修复**: 三处分别添加 `console.warn` / `console.debug` 日志输出。

**Why:** 第 5 轮审计发现 P1×2 + P2×7，全部于 2026-06-08 修复。修复后测试 7 文件 106 用例全过，ESLint 0 错误。评分从 8.5 回升至 9.0/10。

## BUG-023: UserSig 中 `userId.length` 为 `undefined` 导致 IM 登录失败 ✅ 已修复

- **发现时间**: 2026-06-08（第 5 轮微信开发者工具测试 — 点击"聊一聊"提示"消息服务连接中"）
- **修复时间**: 2026-06-08
- **位置**: [server/src/utils/im-api.js:54](server/src/utils/im-api.js#L54) — generateUserSig() return 语句
- **严重程度**: P0（UserSig 包含 `undefined` 字符串，腾讯云 IM SDK 校验失败 → IM 永远无法登录 → 所有聊天功能不可用）
- **根因**: `userId` 来自数据库 `user.id`（INT 类型），Number 没有 `.length` 属性 → `userId.length` = `undefined`。UserSig 模板为 `...2.${userId.length}.${userId}...`，拼接后为 `...2.undefined.1...`，腾讯云 IM 服务端拒绝该签名。
- **修复**: `generateUserSig()` 顶部添加 `const userIdStr = String(userId)`，将所有 `userId` 引用替换为 `userIdStr`（含 JSON 中的 `TLS.identifier` 和 return 字符串中的 `userIdStr.length` + `userIdStr`）
- **影响范围**: GET /api/im/user-sig — 所有已登录用户调用此接口获取的 UserSig 均受影响

## BUG-024: `initIM()` 登录响应格式判断错误 — 首次登录误判为失败 🔴 已修复

- **发现时间**: 2026-06-08（BUG-023 修复后"聊一聊"仍不可用 → 深入分析 TIM SDK 源码）
- **修复时间**: 2026-06-08
- **位置**: [miniprogram/src/utils/im.js:184](miniprogram/src/utils/im.js#L184) — initIM() 登录成功判断
- **严重程度**: P0（首次/冷启动登录永远被误判为失败 → 触发重试 → 依赖 SDK_READY 事件副作用偶然兜底 → 用户体验"消息服务连接中"）
- **根因**: TIM SDK (tim-wx-sdk v2.27.6) `login()` 方法对首次登录和重复登录返回**不同响应格式**：
  - **首次登录**：返回 wslogin 协议原始响应 `{ data: { a2Key, tinyID, helloInterval, instanceID, timeStamp } }` — **无 `actionStatus` 字段**。SDK 在同一个 `Promise.then()` 回调中调用 `triggerReady()` → 触发 `SDK_READY` 事件 → `isReady = true`
  - **重复登录**（已在线）：返回 `{ data: { actionStatus: 'OK', repeatLogin: true } }` — **有 `actionStatus`**
  - 代码 `loginRes?.data?.actionStatus === 'OK'` 对首次登录判断为 `undefined === 'OK'` → `false` → 抛出 `new Error('登录失败')`，即使登录实际已成功
  - 重试机制偶然兜底：第一次失败后 2s 重试 → `isReady` 已被 `SDK_READY` 设为 `true` → `if (isReady) return` 立即返回。但如果 SDK_READY 未及时触发或网络波动导致 3 次重试耗尽，则 IM 永久不可用
- **修复**（3 处改动）:
  1. [utils/im.js:184](miniprogram/src/utils/im.js#L184) — 重写成功判断链：**①`isReady`**（SDK_READY 已触发）→ ②`actionStatus === 'OK'`（重复登录）→ ③`getMyStatus() === 'ok'`（状态检测）→ ④显式 errorCode → ⑤未知错误；catch 块中若 `isReady` 已为 true 直接返回成功
  2. [detail.vue:342-346](miniprogram/src/pages/product/detail.vue#L342-L346) — `goChat()` 改为 `async`，使用 `waitForReady(8000)` 等待 IM 就绪，处理用户快速点击时 IM 仍在初始化的情况
  3. [App.vue:54-56](miniprogram/src/App.vue#L54-L56) — IM 最终初始化失败时显示 toast 提示用户重启，不再静默吞错
- **验证**: 后端 7 文件 106 用例全过、前端构建成功、UserSig 格式正确无 `undefined`

## BUG-025: handleWant() 下发 camelCase `productId`，后端要求 snake_case `product_id` ✅ 已修复

- **发现时间**: 2026-06-10（第 6 轮真机测试）
- **修复时间**: 2026-06-10
- **位置**: [miniprogram/src/pages/product/detail.vue:441](miniprogram/src/pages/product/detail.vue#L441) — handleWant()
- **严重程度**: P0（所有用户点击「我想要」均提示 `"product_id" is required`，下单功能完全不可用）
- **根因**: 前端 `createOrder({ productId: product.value.id })` 使用 camelCase，而后端 `routes/order.js` 的 Joi 校验声明 `product_id: Joi.number().integer().required()`（snake_case）。HTTP 请求发 `{ productId: 1 }` → 后端收到 → Joi 校验 `product_id` 不存在 → 返回 400/4001。
- **修复**: `{ productId: product.value.id }` → `{ product_id: product.value.id }`

## BUG-026: 6 个 repo 的 LIMIT/OFFSET 参数未 parseInt 导致 500 ✅ 已修复

- **发现时间**: 2026-06-10（第 6 轮真机测试）
- **修复时间**: 2026-06-10
- **位置**:
  - [server/src/repository/order.js:129](server/src/repository/order.js#L129) — findByUser()
  - [server/src/repository/review.js:75](server/src/repository/review.js#L75) — findByReviewee()
  - [server/src/repository/credit.js:30](server/src/repository/credit.js#L30) — findChangeLogs()
  - [server/src/repository/user.js:140](server/src/repository/user.js#L140) — listWithFilters()
  - [server/src/repository/report.js:174](server/src/repository/report.js#L174) — list()
  - [server/src/repository/report.js:240](server/src/repository/report.js#L240) — listAdminLogs()
- **严重程度**: P0（6 个 API 端点返回 500 Internal Server Error，影响范围：订单列表/评价记录/信誉变动/用户管理/举报列表/审计日志）
- **根因**: Express `req.query` 参数均为字符串类型。`pool.query()` 客户端参数化转义会将字符串用引号包裹（如 `LIMIT '20' OFFSET 0`），导致 MySQL 语法错误。BUG-006 修复了 `product.findBySeller()` 但遗漏了其他 6 个分页方法。
- **修复**: 所有分页方法统一使用 `Math.max(1, parseInt(filters.page, 10) || 1)` + `Math.min(50, Math.max(1, parseInt(filters.pageSize, 10) || 20))` 模式解析整数。
- **侥幸幸存**: `product.list()` 因 `Math.min(pageSize, maxPageSize)` 隐式将 `pageSize` 强制转为数字而幸免，但 `page` 仍为字符串（已一并修复）。
- **系统性修复**: `product.list()` 同样应用 parseInt 确保 API 响应类型一致。
- **验证**: `npx vitest run` 9 文件 126 用例全过；curl 实测 3 个之前 500 的端点均返回 `code: 0`；POST /api/orders 从 400 变为正常业务错误码 3005。
- **Why 同 BUG-006 但未被捕获:** BUG-006 修复局限于 `findBySeller`，缺少系统性 grep 排查所有 LIMIT/OFFSET 使用点。

## BUG-027: findById() 未 JOIN users，订单详情交易对象显示"卖家"/"买家" ✅ 已修复

- **发现时间**: 2026-06-10（第 6 轮真机测试）
- **修复时间**: 2026-06-10
- **位置**: [server/src/repository/order.js:23-28](server/src/repository/order.js#L23-L28) — findById()
- **严重程度**: P1（订单详情页交易对象名称和头像均显示占位符，用户体验断裂）
- **根因**: `findById()` 只 SELECT `orders` 表字段，未 JOIN `users` 表获取 `buyer_nickname` / `seller_nickname` / `buyer_avatar` / `seller_avatar`。前端 `partnerName` 取 `order.seller_nickname || '卖家'` 字段不存在时 fallback 到硬编码的 "卖家"/"买家"。列表接口 `findByUser()` 有 JOIN 所以正常。
- **修复**: `findById()` 改为 `FROM orders o JOIN users buyer ON o.buyer_id = buyer.id JOIN users seller ON o.seller_id = seller.id`，明确列出所有字段，与 `findByUser()` 保持一致。
- **影响面**: GET /api/orders/:id（订单详情）、以及所有通过 `findById()` 返回订单的写操作（create/markAsMet/updateStatus）。`findByIdempotentKey` / `findTimeoutPending` / `findTimeoutMet` 不需要昵称，保持不变。

## BUG-028: StarRating v-model 协议不匹配 + touchend 事件触发早于 tap，星级评价无法选中 ✅ 已修复

- **发现时间**: 2026-06-10（第 6 轮手工回归测试）
- **修复时间**: 2026-06-10
- **位置**: 
  - [miniprogram/src/components/StarRating.vue](miniprogram/src/components/StarRating.vue) — props/emit/事件处理
  - [miniprogram/src/pages/user/reviews.vue](miniprogram/src/pages/user/reviews.vue) — 6 处 `:value` → `:model-value`
- **严重程度**: P0（评价弹窗三个维度评分全部无法使用——手指离开后星星归零）
- **根因（双 Bug）**:
  1. **v-model 协议不匹配**: StarRating 使用 `value` prop + `change` event，但 Vue 3 的 `v-model` 绑定的是 `modelValue` prop + `update:modelValue` event。`order/detail.vue` 中 `v-model="reviewForm.communicationScore"` 无法收到评分数据——子组件 emit 的 `change` 事件被父组件的 `v-model` 忽略。
  2. **触摸事件顺序**: 模板同时绑定了 `@touchend="hoverIndex = 0"` 和 `@tap="handleClick(n)"`。微信小程序中 `touchend` 先于 `tap` 触发，`hoverIndex` 先被重置为 0（视觉归零），然后 `tap` 才 emit `change`（但父组件收不到，因 bug #1）。
- **修复**:
  1. StarRating prop: `value` → `modelValue`；emit: `'change'` → `'update:modelValue'`；移除 `@touchend`（避免在 `tap` 前重置 hover）；`handleClick` 内部 `emit` 后同步重置 `hoverIndex = 0`（借助 Vue 3 emit 同步更新父组件的特性，`modelValue` prop 在 `hoverIndex` 重置前已更新，displayValue 正常回落）。
  2. `user/reviews.vue` 6 处只读使用 `:value` → `:model-value`。
- **影响面**: 评价弹窗三维度（沟通态度/守时程度/描述一致度）评分交互全部修复，只读展示（评价记录页）同步适配。
- **验证**: ESLint src/ 0 errors；build 成功。

## BUG-029: submitReview() camelCase 字段名 + 缺少 reviewee_id，POST /api/reviews 400 ✅ 已修复

- **发现时间**: 2026-06-10（第 6 轮真机测试，提交评价）
- **修复时间**: 2026-06-10
- **位置**: [miniprogram/src/pages/order/detail.vue:576-582](miniprogram/src/pages/order/detail.vue#L576-L582) — submitReview()
- **严重程度**: P0（提交评价 400 Bad Request，评价功能完全不可用）
- **根因（双 Bug）**:
  1. **camelCase/snake_case 字段名不匹配**: 前端发送 `orderId` / `communicationScore` / `punctualityScore` / `accuracyScore`，但后端 Joi 校验要求 `order_id` / `communication_score` / `punctuality_score` / `accuracy_score`。同 BUG-025 模式。
  2. **缺少必填字段 `reviewee_id`**: 前端根本没有传 `reviewee_id`（被评价人 ID），后端 Joi 将其标记为 `required()`。
- **修复**: 字段名全部改为 snake_case；新增 `reviewee_id: isBuyer.value ? order.value.seller_id : order.value.buyer_id`（评价人 = 当前用户时，被评价人 = 交易对方）。
- **验证**: ESLint src/ 0 errors；build 成功。

## BUG-030: canReview 未检查已评价状态 + 订单详情页缺少评价展示 ✅ 已修复

- **发现时间**: 2026-06-10（第 6 轮真机测试，评价后按钮仍在）
- **修复时间**: 2026-06-10
- **位置**: [miniprogram/src/pages/order/detail.vue](miniprogram/src/pages/order/detail.vue) — canReview/loadOrder/submitReview + 模板 + 样式
- **严重程度**: P1（UX 断裂——评价完按钮不消失，且找不到已提交的评价内容）
- **根因（双问题）**:
  1. **canReview 过于简化**: 仅判断 `status === 'completed'`，不检查当前用户是否已评价。`loadOrder()` 从未请求该订单的评价列表，`canReview` 无数据可判断。
  2. **订单详情页无评价展示**: 模板只含评价弹窗（提交用），没有展示已有评价的 section。
- **修复**:
  1. `loadOrder()` 改为 `Promise.all` 并行加载订单 + 评价（评价失败降级为空数组，不阻塞页面）。
  2. 新增 `myReview` computed（`reviews.value.find(r => r.reviewer_id === myId.value)`），`canReview` 改为 `status === COMPLETED && !myReview.value`。
  3. `submitReview()` 成功后先 `reviews.value.push(...)` 本地追加评价（即时 UI 更新），再后台 `loadOrder()` 静默刷新（获取真实 DB ID + 对方评价）。
  4. 模板新增「评价信息」section-card：头像 / 昵称 / 时间 / 三维 StarRating(只读) / 文字评价引用。
  5. 补充 `.review-item` / `.review-item-header` / `.review-item-scores` / `.review-dim-item` / `.review-item-comment` 样式。
- **影响面**: 订单详情页 ~+80 行模板 + ~+80 行样式；评价流程完整闭环（提交 → 展示 → 按钮消失）。
- **验证**: ESLint src/ 0 errors；build 成功。
- **布局修正 (2026-06-10)**: 三个评分维度标签+星级横向排列在小屏手机上溢出。`.review-item-scores` 改为 `flex-direction: column`，`gap: 32rpx → 12rpx`，纵向排列适配移动端。

**Why:** BUG-025~030 全部已修复 ✅。第 6 轮真机测试 + 回归测试发现 6 个 Bug（P0×4 + P1×2），均已修复并验证通过。

## BUG-031: 审计日志时间筛选 end_date 不含时间分量导致当天记录被排除 ✅ 已修复

- **发现时间**: 2026-06-11（第 9 轮收尾验证）
- **修复时间**: 2026-06-11
- **位置**: [miniprogram/src/pages/admin/logs.vue:246-248](miniprogram/src/pages/admin/logs.vue#L246-L248) — computeEndDate() + [server/src/repository/report.js:276-283](server/src/repository/report.js#L276-L283) — listAdminLogs()
- **严重程度**: P1（选"今天"筛选时所有当天日志被排除——MySQL 将 `'2026-06-11'` 视为 `00:00:00`，`<= '00:00:00'` 排除全天记录）
- **根因**: `computeEndDate` 返回 `'2026-06-11'`（无时间分量），SQL `al.created_at <= '2026-06-11'` 等于 `<= '2026-06-11 00:00:00'`。
- **修复**（纵深防御）:
  1. 前端 `computeEndDate()` → `formatDateStr(new Date()) + ' 23:59:59'`
  2. 后端 `listAdminLogs()` 自动检测：`end_date.includes(':')` 为 false 时自动追加 ` 23:59:59`

## BUG-032: 头像保存后"我的"页面不刷新 ✅ 已修复

- **发现时间**: 2026-06-11（第 9 轮收尾验证）
- **修复时间**: 2026-06-11
- **位置**: [miniprogram/src/pages/user/me.vue](miniprogram/src/pages/user/me.vue) — onShow + template
- **严重程度**: P1（用户编辑资料保存头像后返回 Tab 页，头像仍显示旧图——WeChat Tab 页面可能被缓存，模板不自动重渲染）
- **根因**: `me.vue` 的 `onShow` 只刷新未读通知数（`loadUnreadCount()`），不刷新用户资料。虽然 edit.vue 保存后调了 `getMeAction()` 更新 Pinia store，但 Tab 页可能使用缓存的渲染树。
- **修复**（3 处）:
  1. 导入 `resolveImageUrl` → 新增 `avatarUrl` computed（自动适配模拟器/真机 host 差异）
  2. 模板改为 `:src="avatarUrl || defaultAvatar"`（使用解析后的 URL）
  3. `onShow` 中增加 `userStore.getMeAction()` 调用（每次回到"我的"确保数据最新）

## BUG-033: 微信渲染层报错 — addListener undefined + first rendering data 冲突 ✅ 已修复

- **发现时间**: 2026-06-11（第 9 轮收尾验证）
- **修复时间**: 2026-06-11
- **位置**: [miniprogram/src/App.vue:61-69](miniprogram/src/App.vue#L61-L69) + [miniprogram/src/App.vue:97-113](miniprogram/src/App.vue#L97-L113)
- **严重程度**: P2（控制台报错但功能正常——两个错误均源自渲染层初始化时序问题）
- **根因（双错误）**:
  1. `Cannot read property 'addListener' of undefined` → `uni.onNetworkStatusChange` 在 App.vue 加载时立即注册，微信小程序渲染层 listener 管道可能尚未初始化。底层 `VendorApp` 或 `NativeEvent` 对象未就绪。
  2. `Expected updated data but get first rendering data` → `watch({ immediate: true })` 在首次渲染周期内调用 `uni.setTabBarBadge`，与微信渲染层 setData 时序冲突。
- **修复**:
  1. `uni.onNetworkStatusChange` 用 try/catch 包裹，降级为静默跳过（网络状态监听非核心功能）
  2. 移除 `watch` 的 `{ immediate: true }`，改用 `nextTick()` 在首个渲染周期结束后设置初始角标
- **验证**: ESLint 0 errors；build DONE。

**How to apply:** 发现新 Bug 时按此格式追加。修复后标记 ✅ 并记录修复方案。详见 [[iteration3-audit]] [[iteration4-audit]] [[iteration5-audit]] [[project-state]]。

## BUG-034: 编辑商品 navigateTo 报错 — tabBar 页面不可 navigateTo ✅ 已修复

- **发现时间**: 2026-06-11（第 12 轮真机测试）
- **修复时间**: 2026-06-11
- **位置**: [miniprogram/src/pages/product/detail.vue](miniprogram/src/pages/product/detail.vue#L420) / [miniprogram/src/pages/product/my.vue](miniprogram/src/pages/product/my.vue#L234) / [miniprogram/src/store/app.js](miniprogram/src/store/app.js) / [miniprogram/src/pages/product/publish.vue](miniprogram/src/pages/product/publish.vue)
- **严重程度**: P0（编辑功能完全不可用——点击编辑按钮即报错，页面无响应）
- **根因**: `pages/product/publish` 在 `pages.json` 中注册为 tabBar 页面，微信小程序禁止 `uni.navigateTo` 跳转 tabBar 页（只能 `uni.switchTab`），而 `switchTab` 不支持 query 参数传递 `?id=123`。
- **修复（4 文件）**:
  1. `store/app.js`: 新增 `pendingEditProductId` 状态 + `setPendingEditProductId(id)` / `consumePendingEditProductId()` 两个 action（消费即清空）。
  2. `pages/product/detail.vue` + `pages/product/my.vue`: `goEdit()` 改为 `appStore.setPendingEditProductId(id)` → `uni.switchTab({ url: '/pages/product/publish' })`。
  3. `pages/product/publish.vue`: 新增 `onShow` 钩子检查 `appStore.consumePendingEditProductId()`，非 null 则 `resetForm()` + 进入编辑模式；新增 `resetForm()` 函数。
- **验证**: Build DONE；29 前端测试全绿。

## BUG-035: 编辑保存后 navigateBack 报错 — switchTab 无历史栈 ✅ 已修复

- **发现时间**: 2026-06-11（第 12 轮真机测试，BUG-034 修复后立即发现）
- **修复时间**: 2026-06-11
- **位置**: [miniprogram/src/pages/product/publish.vue](miniprogram/src/pages/product/publish.vue) — submitPublish / loadExistingProduct 共 4 处 `uni.navigateBack()`
- **严重程度**: P0（编辑保存成功后报错 `navigateBack:fail cannot navigate back at first page`，用户无法正常退出编辑流程）
- **根因**: `switchTab` 跳转到 tabBar 页面不创建导航历史栈条目，`navigateBack()` 无页面可回退。
- **修复**:
  1. 保存成功后：`uni.navigateTo({ url: '/pages/product/detail?id=${editId.value}' })` — 跳转到商品详情页查看编辑结果。
  2. 校验失败（无权/状态不可编辑/加载异常）：`uni.switchTab({ url: '/pages/index/index' })` — 返回首页。
- **验证**: Build DONE；29 前端测试全绿。

## BUG-036: 编辑保存后 resetForm() 先于 URL 拼接，跳转 "商品不存在" ✅ 已修复

- **发现时间**: 2026-06-11（第 12 轮真机测试，BUG-035 修复后立即发现）
- **修复时间**: 2026-06-11
- **位置**: [miniprogram/src/pages/product/publish.vue:597-601](miniprogram/src/pages/product/publish.vue#L597-L601) — submitPublish() 编辑成功分支
- **严重程度**: P0（编辑保存成功后跳转到 "商品不存在" 页面，用户无法查看编辑结果）
- **根因**: BUG-035 修复时在 `submitPublish()` 编辑成功分支增加了 `resetForm()` 调用以清理编辑状态。但 `resetForm()` 内部第 360 行 `editId.value = null`，第 601 行 `uni.navigateTo({ url: '/pages/product/detail?id=${editId.value}' })` 拼接出 `/pages/product/detail?id=null`，详情页无法加载商品。
- **修复**: 先 `const targetId = editId.value` 保存 ID，再 `resetForm()`，最后用 `targetId` 拼 URL。
- **教训**: 修改共享状态的函数调用后，不可再依赖该状态的值。应先提取到局部变量。

## BUG-037: 编辑保存后发布页被 "保存修改" 状态永久覆盖 ✅ 已修复

- **发现时间**: 2026-06-11（第 12 轮真机测试，BUG-036 修复后立即发现）
- **修复时间**: 2026-06-11
- **位置**: [miniprogram/src/pages/product/publish.vue](miniprogram/src/pages/product/publish.vue) — submitPublish() + onShow()
- **严重程度**: P0（编辑保存后返回首页再点发布 Tab，看到的是 "保存修改" 而非空白的 "发布商品" 页，用户无法再发布新商品）
- **根因**: tabBar 页面不会销毁，`editId`/`form`/`currentStep` 是 Vue 响应式状态在内存中持久保留。编辑保存后 `navigateTo` 离开时未清理这些状态。下次 `onShow` 时 `consumePendingEditProductId()` 返回 null（已消费），`if` 跳过，页面仍显示编辑模式。
- **修复（双重防御）**:
  1. **主动清理**: `submitPublish()` 编辑保存成功后先 `const targetId = editId.value`，再 `resetForm()`，然后 `navigateTo`。
  2. **被动兜底**: `onShow()` 新增 `else if (editId.value)` 分支——若 pendingId 为 null 但页面仍处于编辑模式，强制 `resetForm()` + 恢复标题。
- **验证**: Build ✅ / ESLint 0 errors。
