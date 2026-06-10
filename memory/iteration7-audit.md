---
name: iteration7-audit
description: 第 7 轮代码全面审阅报告 — 举报/管理后台/互评增强（P0×0/P1×4/P2×5）
metadata:
  type: feedback
---

# 第 7 轮代码审阅报告

**审阅日期:** 2026-06-10
**审阅范围:** 第 7 轮全部改动（11 文件，~1550 行新增）
**审阅方法:** 逐文件通读 + 架构一致性检查 + 安全审计 + 性能审视

---

## 审阅结论

**综合评分: 8.5/10**（良好，无阻塞性问题）

| 级别 | 数量 | 说明 |
|------|:----:|------|
| P0 阻塞 | 0 | — |
| P1 重要 | 4 | 需在下轮迭代前修复 |
| P2 建议 | 5 | 可延后处理 |

---

## 一、改动清单确认

### 后端（4 文件，+48 行）

| 文件 | 改动 | 状态 |
|------|------|:--:|
| [server/src/repository/report.js](server/src/repository/report.js) | 新增 `findDetailById(id)` — JOIN 查询含 reporter/reported 用户信息 | ✅ |
| [server/src/services/report.js](server/src/services/report.js) | 新增 `detail(reportId, userId, userRole)` — 含权限校验 | ✅ |
| [server/src/controllers/report.js](server/src/controllers/report.js) | 新增 `detail` 控制器方法 | ✅ |
| [server/src/routes/report.js](server/src/routes/report.js) | 新增 `GET /:id` 路由 | ✅ |

### 前端 API（2 文件，+226 行）

| 文件 | 端点 | 状态 |
|------|------|:--:|
| [miniprogram/src/api/report.js](miniprogram/src/api/report.js) | `createReport` / `getReportList` / `getReportDetail` | ✅ |
| [miniprogram/src/api/admin.js](miniprogram/src/api/admin.js) | 14 个端点：工单 3 + 用户 3 + 商品 1 + 统计 4 + 敏感词 3 + 日志 1 | ✅ |

### 前端页面（5 文件，~1280 行）

| 文件 | 类型 | 行数 | 状态 |
|------|------|:--:|:--:|
| [miniprogram/src/pages/report/submit.vue](miniprogram/src/pages/report/submit.vue) | 新建（从 stub 重写） | ~260 | ✅ |
| [miniprogram/src/pages/report/detail.vue](miniprogram/src/pages/report/detail.vue) | 新建（从 stub 重写） | ~230 | ✅ |
| [miniprogram/src/pages/admin/tickets.vue](miniprogram/src/pages/admin/tickets.vue) | 新建（从 stub 重写） | ~350 | ✅ |
| [miniprogram/src/pages/admin/dashboard.vue](miniprogram/src/pages/admin/dashboard.vue) | 新建（从 stub 重写） | ~300 | ✅ |
| [miniprogram/src/pages/order/detail.vue](miniprogram/src/pages/order/detail.vue) | 增强 | +~140 | ✅ |

---

## 二、P1 重要问题（4 项）

### P1-01: `tickets.vue` — 裁决弹窗在 submitting 期间仍可关闭

**文件:** [miniprogram/src/pages/admin/tickets.vue:343](miniprogram/src/pages/admin/tickets.vue#L343-L346)

**问题:** `closeResolveModal()` 不检查 `resolveModal.submitting` 状态。用户在裁决提交过程中点击遮罩层关闭弹窗，不会中断请求，但弹窗关闭后用户可能再次打开并重复提交。

**建议修复:**

```js
function closeResolveModal() {
  if (resolveModal.submitting) return;  // ← 提交中禁止关闭
  resolveModal.visible = false;
  resolveModal.ticket = null;
}
```

同时在弹窗遮罩层上：`@click="resolveModal.submitting ? null : closeResolveModal()"`

**严重程度:** 中 — 后端 UNIQUE 约束 + 状态机可以防御重复提交，但前端应避免 UX 歧义。

---

### P1-02: `dashboard.vue` — 权限守卫使用模块级同步判断，可能在 store 未就绪时误判

**文件:** [miniprogram/src/pages/admin/dashboard.vue:155](miniprogram/src/pages/admin/dashboard.vue#L155-L161)

**问题:** `userRole` 在 `<script setup>` 顶层求值：
```js
const userRole = userStore.user?.role || 'user';
if (userRole !== 'admin') {
  uni.showToast(...);
  setTimeout(() => uni.navigateBack(), 2000);
}
```
`onShow` 中又重复检查了 `if (userRole !== 'admin') return`，但顶层 if 判断不阻止后续代码执行——脚本会继续运行，`loadDashboard` 和模板渲染在 `onShow` 触发前就已经开始。如果 Pinia store 尚未从持久化恢复（极少数竞态场景），`userRole` 会回退到 `'user'`，页面会短暂渲染空数据态后才跳转。

**建议修复:** 将权限检查改为 `computed`，并在模板中使用 `v-if` 守卫：

```js
const userRole = computed(() => userStore.user?.role || 'user');
```

模板顶部：
```html
<view v-if="userRole !== 'admin'" class="status-center">
  <text>仅管理员可访问</text>
</view>
<template v-else>
  <!-- 原有内容 -->
</template>
```

**严重程度:** 低-中 — 实际复现概率极低（需 store 持久化未完成 + 用户手动导航到此页），但 tickets.vue 有同样模式。

---

### P1-03: `order/detail.vue` — 本地评价插入用 `Date.now()` 作临时 ID，极端场景可能冲突

**文件:** [miniprogram/src/pages/order/detail.vue:712](miniprogram/src/pages/order/detail.vue#L712-L724)

**问题:** `submitReview` 成功后向 `reviews.value` push 一个临时评价对象，使用 `Date.now()` 作为 `id`。如果在同一毫秒内用户连续操作（虽不可能但代码防御不足），`v-for` key 可能重复。

**建议修复:** 更好的做法是回退到后端返回的 ID，或在 `loadOrder()` 完成后用真实数据替换：

```js
// 方案 A: 使用服务器返回的 review id
const created = await createReview({...});
reviews.value.push({...created, ...localOverrides });

// 方案 B: 带前缀的临时 ID 避免与真实 ID 冲突
id: `temp_${Date.now()}`,
```

**严重程度:** 低 — 实际碰撞概率可忽略，但属于防御性编程范畴。

---

### P1-04: 缺少 `GET /api/reports/:id` 端点测试

**文件:** `server/__tests__/unit/services/report.test.js`（不存在）

**问题:** 第 7 轮新增的后端端点 `GET /api/reports/:id` 没有对应的单元测试。虽然端点的业务逻辑很简单（查数据库 + 权限判断），但根据 rules/ 测试规范"新增业务逻辑必须包含测试"。

**建议:** 至少覆盖 3 条用例：
1. 举报人查看自己的举报 → 成功
2. cs/admin 查看他人举报 → 成功
3. 非举报人非 cs/admin 查看 → 抛出 notOwner

**严重程度:** 低-中 — 端点逻辑简单，且手动测试已覆盖，但违反测试规范。

---

## 三、P2 建议优化（5 项）

### P2-01: `submit.vue` — 表单双重校验

**文件:** [miniprogram/src/pages/report/submit.vue:152](miniprogram/src/pages/report/submit.vue#L152-L154)

`canSubmit` computed 和 `handleSubmit` 函数都做了 `form.type` 和 `description.length >= 10` 校验。`canSubmit` 用于按钮 disabled 状态，`handleSubmit` 做冗余检查。这不是 bug（防御性合理），但可以简化为只依赖 `canSubmit` + 按钮 disabled。

**建议:** 保持现状即可 — 冗余校验在客户端是低成本安全网。

---

### P2-02: `tickets.vue` — `hasMore` 判断使用 `length >= pageSize` 而非 `total`

**文件:** [miniprogram/src/pages/admin/tickets.vue:267](miniprogram/src/pages/admin/tickets.vue#L267)

```js
hasMore.value = (result.list || []).length >= pageSize;
```

当最后页恰好返回 `pageSize` 条数据时，`hasMore` 仍为 `true`，用户需要再滑一次才能看到"没有更多"。使用 `result.total` 比较更精确。

**建议:** 
```js
hasMore.value = tickets.value.length < result.total;
```

---

### P2-03: `dashboard.vue` — 权限校验页面闪烁

**文件:** [miniprogram/src/pages/admin/dashboard.vue:158](miniprogram/src/pages/admin/dashboard.vue#L158-L161)

与 P1-02 同根。非 admin 用户访问时，`setTimeout(() => uni.navigateBack(), 2000)` 期间页面会短暂显示加载态（`loading` 初始为 `true`）。用户体验上会看到加载中 → 跳转，而非直接跳转。

**建议:** 将 `loading` 初始值设为 `false`，或添加一个 `notAuthorized` 标志配合模板 `v-if` 提前拦截渲染。

---

### P2-04: `detail.vue` — `evidenceImages` computed 依赖 `JSON.parse` 容错

**文件:** [miniprogram/src/pages/report/detail.vue:163](miniprogram/src/pages/report/detail.vue#L163-L174)

当前实现同时兼容 `string[]` 和 JSON 字符串两种格式。这是因为 MySQL 的 JSON 列通过 `mysql2` 返回时可能是字符串也可能是已解析对象，取决于 `mysql2` 驱动的类型转换配置。双路径处理是正确的防御策略，但应在 repository 层统一解析，而非让每个消费者都做兼容。

**建议:** 在 `reportRepo.findDetailById` 中统一 `JSON.parse(evidence_images)`，确保 service 层以上拿到的始终是 `string[]`。降低前端复杂度。

---

### P2-05: `submit.vue` — 商品标题异步加载无加载指示

**文件:** [miniprogram/src/pages/report/submit.vue:232](miniprogram/src/pages/report/submit.vue#L232-L238)

```js
getProductDetail(productId.value)
  .then((product) => { productTitle.value = product.title || ''; })
  .catch(() => { /* 静默失败 */ });
```

如果请求较慢，用户在表单填写过程中，商品标题会突然出现。体验上可以接受但不够精致。

**建议:** 添加 `productTitleLoading` 标志，在加载时显示占位文字。

---

## 四、安全检查结论

| 检查项 | 结果 |
|--------|:--:|
| JWT 鉴权覆盖 | ✅ — `app.use('/api', auth)` 全局保护所有 `/api/*` 路由 |
| SQL 注入防护 | ✅ — 全部使用参数化查询（`?` 占位符） |
| 权限校验在 service 层 | ✅ — `reportService.detail()` 检查 `reporter_id \|\| cs \|\| admin` |
| 管理员操作审计 | ✅ — `reportRepo.createAdminLog()` 记录所有管理操作 |
| XSS 防护 | ✅ — 全部使用 `<text>` 组件，无 `v-html` 使用 |
| 输入校验 | ✅ — report 路由使用 Joi 校验 `type`/`description`/`evidence_images` |
| 敏感信息泄露 | ✅ — 日志中无密码/Token 输出 |
| 硬编码密钥 | ✅ — 无硬编码密钥 |

---

## 五、架构一致性检查

| 检查项 | 结果 |
|--------|:--:|
| 5 层架构贯彻 | ✅ — routes → controllers → services → repository → models |
| 统一返回格式 `{code, message, data}` | ✅ |
| Repository 禁止 `SELECT *` | ✅ — `findDetailById` 使用明确字段列表 |
| Service 层业务逻辑 + 权限 | ✅ — `detail()` 含权限判断 |
| Controller 只做参数提取 + 响应 | ✅ |
| 前端 targetPage 分页模式 | ✅ — tickets.vue 使用先计算目标页再 fetch 模式 |
| 前端 ImageUploader + chooseAndUpload 模式 | ✅ — submit.vue 遵循已有模式 |
| SCSS 仅使用 tokens.scss 变量 | ✅ — 全部 `$color-*`/`$space-*`/`$text-*`/`$radius-*`/`$font-*` 已验证存在 |
| Vue 3 Composition API | ✅ — 全部使用 `<script setup>` + `ref`/`reactive`/`computed` |

---

## 六、代码质量亮点

1. **`reportService.detail()` 的懒加载 require** — 与 `orderService` 中的 `notOwner` 引用模式保持一致（`const { notOwner } = require('../utils/errors')`），避免循环依赖。

2. **`evidenceImages` computed 的双格式兼容** — 同时处理 `string[]` 和 JSON 字符串，兼容 `mysql2` 驱动的不同 JSON 解析行为。

3. **CSS-only 图表方案** — dashboard.vue 的热门分类进度条 + 每日订单柱状图均使用 flex + CSS 实现，零第三方依赖，包体积友好。

4. **`getBarWidth` 最小宽度守卫** — `Math.max(4, (count / max) * 100)` 确保即使数值很小也有可见的色条，避免空进度条歧义。

5. **评价失败的错误码分发** — `submitReview` 的 catch 分支分别处理 `3006`（重复）、`3001`（状态错误）和其他错误，提示精确。

6. **`Promise.all` 并行加载** — `loadOrder`（订单详情 + 评价列表）和 `loadDashboard`（看板 + 敏感词统计）均使用并行请求，减少串行等待。

7. **互评进度条** — `reviewCompletion` computed 驱动时间线节点 4 的激活状态 + 进度条宽度 + 提示文字，一个计算属性驱动三处 UI。

---

## 七、与第 6 轮审阅对比

| 指标 | 第 6 轮 | 第 7 轮 | 趋势 |
|------|:--:|:--:|:--:|
| P0 问题 | 2 | 0 | ↑ 改善 |
| P1 问题 | 5 | 4 | ↑ 改善 |
| P2 问题 | 4 | 5 | → 持平 |
| 综合评分 | 8.5/10 | 8.5/10 | → 持平 |
| 代码行数 | ~1200 | ~1550 | — |
| 改动文件数 | 12 | 11 | — |
| 测试覆盖 | 126/126 | 126/126 | → 持平（无新增测试） |

---

## 八、修复优先级建议

| 优先级 | 条目 | 预计工作量 |
|--------|------|:--:|
| 🔴 下轮迭代修复 | P1-01 裁决弹窗防误关 | 5 分钟 |
| 🔴 下轮迭代修复 | P1-02 权限守卫改用 computed | 15 分钟（含 tickets.vue 同步修复） |
| 🟡 下轮迭代修复 | P1-03 临时 ID 加前缀 | 2 分钟 |
| 🟡 可延后 | P1-04 补 report detail 测试 | 20 分钟 |
| 🟢 可延后 | P2-01~P2-05 | 视情况处理 |

---

## 九、验证记录

| 检查项 | 命令 | 结果 |
|--------|------|:--:|
| 后端单元测试 | `npx vitest run` | 126/126 ✅ |
| 前端 ESLint | `npx eslint --ext .js,.vue src/` | 0 errors ✅ |
| 前端构建 | `npm run build:mp-weixin` | DONE ✅ |

---

## 十、相关记忆

- [[project-state]] — 项目开发进度总览
- [[iteration6-audit]] — 第 6 轮审阅报告（对比参考）
- [[known-bugs]] — 已知 Bug 记录（P1-01~P1-04 可记录于此）
- [[p1-transaction-fixes]] — P1 事务原子操作修复（第 5 轮）
