---
name: iteration5-audit
description: 第 5 轮（聊天 IM + 通知中心）全面代码审计 — 7 维度 16 文件，发现 P1×2 + P2×8，综合评分 8.7/10
metadata:
  type: project
  updatedAt: 2026-06-08
  iteration: 5
  filesAudited: 7 backend + 9 frontend
  totalLines: ~2600
---

# 第 5 轮全面代码审计

**审计日期：** 2026-06-08
**审计范围：** 第 5 轮全部 16 个改动文件 + 上下游依赖文件
**审计维度：** SQL 安全 → 数据流 → 状态管理 → 边界用例 → 安全 → 性能 → 测试覆盖
**基线：** 7 文件 106 测试全过，ESLint 0 错误（20 有意 console.warn）
**修复日期：** 2026-06-08（P1×2 + P2×6 全部修复，A5-003 不修复）

---

## 一、发现汇总

| ID | 级别 | 位置 | 简述 | 状态 |
|:--:|:--:|------|------|:--:|
| A5-001 | P1 | `chat/detail.vue:312-313` | 发送消息前清空输入框 → 发送失败时用户已输入文本丢失 | ✅ 已修复 |
| A5-002 | P1 | `chat/detail.vue:339-351` | MESSAGE_RECEIVED 监听器在页面隐藏时仍处理消息并标记已读 | ✅ 已修复 |
| A5-003 | P2 | `chat/detail.vue:372-386` | `processMessages()` 未处理消息乱序（IM SDK 可能返回逆序） | ℹ️ 不修复 |
| A5-004 | P2 | `chat/list.vue:273` | `tim.EVENT?.CONVERSATION_UPDATE \|\| 'conversation-updated'` — 兜底字符串无效 | ✅ 已修复 |
| A5-005 | P2 | `chat/list.vue:224-225` | `onTapConversation` 乐观清零 unreadCount → setMessageRead 失败时不一致 | ✅ 已修复 |
| A5-006 | P2 | `notification/index.vue:160` | `hasMore` 用 `length >= 20` 判断，末页恰好满 20 条时多一次空请求 | ✅ 已修复 |
| A5-007 | P2 | `notification/index.vue:238-239` | 空 catch — "标记失败不阻塞"，违反 error-handling-rules | ✅ 已修复 |
| A5-008 | P2 | `App.vue:131-133` | 空 catch — 通知轮询静默失败，违反 error-handling-rules | ✅ 已修复 |
| A5-009 | P2 | `detail.vue:464-465` | 空 catch — JSON 解析静默失败，违反 error-handling-rules | ✅ 已修复 |
| A5-010 | P3 | `chat/list.vue` vs `chat/detail.vue` | 事件监听器清理不一致 — list.vue 无 onUnload 清理，但 TabBar 页面不需要 | ℹ️ 可接受 |

**P0：** 0 个 | **P1：** 2 个（✅ 已修复） | **P2：** 7 个（✅ 6 个已修复 + ℹ️ 1 个不修复） | **P3：** 1 个（可接受）

---

## 二、后端逐层审计

### 2.1 IM Controller（`server/src/controllers/im.js` — 32 行，新建）

#### ✅ 正确项

1. **单一职责**：仅提取 `req.user.id` → 调用 `imProvider.generateUserSig()` → 组装响应。无业务逻辑。
2. **JWT 鉴权依赖**：端点受 `app.use('/api', auth)` 保护，用户无法伪造他人 UserSig。
3. **错误透传**：`catch (err) → next(err)`，由全局 error handler 统一处理。

#### 📝 无独立发现

Controller 极简（32 行），逻辑正确，无安全问题。

---

### 2.2 IM Route（`server/src/routes/im.js` — 14 行，新建）

#### ✅ 正确项

1. **注释说明鉴权来源**：明确声明 JWT 由 `app.js` 全局应用，此处不重复添加。
2. **路径简洁**：仅一个 `GET /user-sig`，挂载到 `/api/im`。

#### 📝 无独立发现

---

### 2.3 IM REST API 封装（`server/src/utils/im-api.js` — 99 行，重构后唯一源）

#### ✅ 正确项

1. **UserSig 格式**：遵循腾讯云 TLS v2 规范 — `base64Url(JSON).base64Url(HMAC).version.userIdLen.userId.sdkAppId.expire.time`。
2. **HMAC-SHA256**：使用 Node.js crypto 模块原生实现，无第三方依赖。
3. **Base64Url 编码**：正确替换 `=` → `''`、`+` → `*`、`/` → `-`（腾讯云 IM 特殊字符映射）。
4. **REST API 调用**：`buildRestUrl()` 对 identifier + userSig 做 `encodeURIComponent()` → 防 URL 注入。
5. **错误统一返回**：`callRestApi()` 始终返回 `{ success, data/error }` 结构，调用方无需 try-catch。

#### 📝 无独立发现

重构后 `im-api.js` / `tencent.js` / `provider.js` 三层清晰：
- `im-api.js`：底层 URL 签名 + HTTP 调用（可独立测试）
- `tencent.js`：业务语义封装（sendSystemMessage、createConversation）
- `provider.js`：抽象接口 + 动态加载（可切换 IM 厂商）

---

### 2.4 IM Provider（`server/src/services/im/tencent.js` — 83 行，重构 -80 行）

#### ✅ 正确项

1. **消除重复**：`generateUserSig` 改为从 `utils/im-api.js` 引入，不再本地实现。
2. **sendSystemMessage 正确**：
   - `From_Account` 使用管理员账号（有推送权限）
   - `MsgRandom` 防重放
   - 支持单发（`To_Account = String`）和群发（`To_Account = Array`）
3. **失败日志**：REST API 调用失败时记录 warn 日志，调用方可继续处理。

#### 📝 无独立发现

---

### 2.5 后端测试（`server/__tests__/integration/im.test.js` — 130 行，新建）

#### ✅ 覆盖项（8 用例）

| 维度 | 覆盖场景 |
|------|---------|
| 鉴权 | IM-001: 已登录获取 UserSig / IM-002: 无头/无效 token |
| 数据字段 | IM-001: userId 与登录用户一致 |
| 格式校验 | IM-003: 非空字符串 / 长度>50 / Base64Url 字符集 / sdkAppId 正整数 |

#### 📝 未覆盖边界

| 场景 | 重要性 |
|------|:--:|
| UserSig 过期时间正确性 | P2 |
| 并发获取 UserSig（同一用户多次请求） | P3 |
| 被封禁用户获取 UserSig（应由 auth 中间件拦截） | P3 |

---

## 三、前端逐层审计

### 3.1 IM 初始化模块（`miniprogram/src/utils/im.js` — 330 行，重写）

#### ✅ 正确项

1. **单例模式**：`tim` 实例全局唯一，`createTIM()` 幂等。
2. **登录重试**：指数退避（1s→2s→4s），最多 3 次。
3. **就绪队列**：`waitForReady()` 将回调缓存 → SDK_READY 时批量执行。
4. **事件注册完整**：SDK_READY / SDK_NOT_READY / MESSAGE_RECEIVED / CONVERSATION_UPDATE / KICKED_OUT / NET_STATE_CHANGE — 6 个事件全覆盖。
5. **SDKAppID 硬编码**：`1600145841` 作为创建实例的初始值（后续 login 会使用服务端返回的正确值）。
6. **容错设计**：`getTotalUnreadMessageCount` 做 feature detection；`_updateUnreadCount` 全 try-catch 包裹。
7. **isIMReady() 双检查**：`isReady && tim !== null` — 防止 SDK 未初始化但 isReady 意外为 true。

#### 📝 无独立发现

代码质量高，是前端 IM 集成的核心基础设施。单例 + 事件驱动 + 重试机制设计合理。

---

### 3.2 会话列表（`miniprogram/src/pages/chat/list.vue` — 470 行，重写）

#### ✅ 正确项

1. **数据转换**：`mapConversation()` 正确从 SDK 格式提取 nickname/avatar/summary/lastTime/unreadCount。
2. **摘要截断**：文本消息取前 20 字、自定义消息显示 `[系统通知]`。
3. **时间格式化**：刚刚/分钟前/小时前/昨天/月/日 → 5 级粒度。
4. **下拉刷新**：`refresher-enabled` + `@refresherrefresh`。
5. **长按删除**：`uni.showActionSheet` → 确认 → `deleteConversation()` → 本地移除。
6. **空状态设计**：emoji + 引导文字 + 去逛逛按钮。

#### A5-004 [P2] `tim.EVENT?.CONVERSATION_UPDATE || 'conversation-updated'` — 兜底字符串无效

```js
// chat/list.vue:273
tim.on(tim.EVENT?.CONVERSATION_UPDATE || 'conversation-updated', () => {
  loadConversations();
});
```

`'conversation-updated'` 不是腾讯云 IM SDK 的有效事件名。如果 `tim.EVENT` 为 undefined（极端情况），事件监听静默失败，会话列表不再实时更新。`tim.EVENT` 在 SDK 正常加载时必定存在，但兜底值应使用有效事件名或直接硬编码字符串 `'conversation-updated'` → 应为 `TIM.EVENT.CONVERSATION_UPDATE`（与 detail.vue 一致，直接 import TIM）。

**风险：** 极低（`tim.EVENT` 不会 undefined），但代码意图不清晰。
**修复：** 改为 `import TIM from 'tim-wx-sdk'` → `tim.on(TIM.EVENT.CONVERSATION_UPDATE, ...)`。

#### A5-005 [P2] 乐观清零 unreadCount

```js
// chat/list.vue:223-226
function onTapConversation(conv) {
  if (conv.unreadCount > 0) {
    setMessageRead(conv.conversationID);  // 异步，无 await
    conv.unreadCount = 0;                  // 立即清零
  }
  uni.navigateTo({ ... });
}
```

`setMessageRead()` 是异步的，但未 await。如果调用失败，UI 已清零但 IM 服务端未标记已读。下次 `loadConversations()` 会恢复正确值（`onShow` + `CONVERSATION_UPDATE` 双重保障）。

**风险：** 短暂 UI 不一致（秒级），可自愈。
**修复：** 添加 `.catch()` 恢复 unreadCount，或改为 `await` + try-catch。

---

### 3.3 聊天详情（`miniprogram/src/pages/chat/detail.vue` — 720 行，重写）

#### ✅ 正确项

1. **消息接收监听注册/清理**：`onLoad` 注册 → `onUnload` 移除。生命周期正确。
2. **历史加载**：`loadMoreHistory()` 前置插入 + `recomputeShowTimes()` 重算时间标签。
3. **滚动控制**：`scroll-into-view` + `nextTick` toggle → 强制触发滚动。
4. **消息气泡 UI**：out（蓝底白字右对齐 + 发送状态） vs in（白底灰字左对齐 + 头像）。
5. **时间标签**：≥5 分钟间隔显示，支持今天/昨天/年内/更早格式。
6. **系统消息**：`TIMCustomElem` 类型 → 居中卡片样式，尝试多种路径提取文本。
7. **输入栏**：`:disabled="sending"` 防重复发送，`:hold-keyboard="true"` 保持键盘不收起。
8. **防重复加载**：`loadingHistory` / `loadingInitial` 守卫。

#### A5-001 [P1] 发送失败丢失用户输入

```js
// chat/detail.vue:308-326
async function sendMessage() {
  const text = inputText.value.trim();
  if (!text || sending.value) return;

  sending.value = true;
  inputText.value = '';     // ← 🔴 在 await 之前清空！

  try {
    const msg = await sendTextMessage(conversationId.value, text);
    // ...
  } catch (err) {
    // 🔴 用户的输入已丢失，text 变量在闭包中但 inputText ref 已清空
    uni.showToast({ title: err.message || '发送失败', icon: 'none', duration: 1500 });
  } finally {
    sending.value = false;
  }
}
```

**根因：** `inputText.value = ''` 在 `await sendTextMessage()` 之前执行。如果发送失败（网络异常、被踢下线等），用户已输入的文本永久丢失。

**影响：** 用户打了一段长消息 → 点击发送 → 网络波动导致失败 → 输入框已空 → 消息丢失。用户体验差。

**修复方案：**
```js
async function sendMessage() {
  const text = inputText.value.trim();
  if (!text || sending.value) return;

  sending.value = true;
  // 不在这里清空 inputText

  try {
    const msg = await sendTextMessage(conversationId.value, text);
    inputText.value = '';  // ← 成功后才清空
    const processed = processMessages([msg]);
    messages.value.push(...processed);
    scrollToBottom();
  } catch (err) {
    // inputText 保留用户输入，可重新发送
    uni.showToast({ title: err.message || '发送失败', icon: 'none', duration: 1500 });
  } finally {
    sending.value = false;
  }
}
```

#### A5-002 [P1] 页面隐藏时仍处理消息并标记已读

```js
// chat/detail.vue:335-355
function registerMessageListener() {
  const tim = getTim();
  if (!tim) return;

  onMessageReceived = (event) => {
    const newMessages = (event.data || []).filter(
      (m) => m.conversationID === conversationId.value
    );
    if (newMessages.length === 0) return;

    const processed = processMessages(newMessages);
    messages.value.push(...processed);

    setMessageRead(conversationId.value);  // 🔴 页面隐藏时也标记已读
    scrollToBottom();
  };

  tim.on(TIM.EVENT.MESSAGE_RECEIVED, onMessageReceived);
}
```

**场景复现：**
1. 用户在 C2C 聊天详情页（与卖家 A）
2. 点击系统消息中的链接 → `uni.navigateTo` 跳转到商品详情页
3. 聊天详情页被隐藏（`onHide` 触发，但 `onUnload` 未触发—页面仍在栈中）
4. 卖家 A 发来新消息 → `MESSAGE_RECEIVED` 触发
5. 监听器仍在运行 → 消息被处理 + `setMessageRead()` 标记已读
6. 用户返回聊天详情页 → 看不到未读提示（已被标记已读）

**影响：** 用户在浏览其他页面时，聊天消息被静默标记已读。用户回到聊天页可能错过重要消息。

**修复方案：** 在 `onHide` 中暂停处理（移除监听器或设置 flag），在 `onShow` 中恢复：
```js
// 新增状态
const isPageVisible = ref(true);

onHide(() => {
  isPageVisible.value = false;
});

onShow(() => {
  isPageVisible.value = true;
  // 恢复时刷新消息列表并标记已读
  if (conversationId.value) {
    setMessageRead(conversationId.value);
    loadLatestMessages(); // 拉取隐藏期间的消息
  }
});

// 在 onMessageReceived 中检查
onMessageReceived = (event) => {
  if (!isPageVisible.value) return; // 页面隐藏时跳过
  // ... 原有逻辑
};
```

#### A5-003 [P2] `processMessages()` 未处理消息乱序

```js
// chat/detail.vue:366-387
function processMessages(list) {
  const prev = messages.value;
  const lastPrevTime = prev.length > 0 ? prev[prev.length - 1].time : 0;

  return list.map((msg, i) => {
    let showTime = false;
    if (prev.length === 0 && i === 0) {
      showTime = true;
    } else if (i === 0 && lastPrevTime > 0) {
      showTime = (msg.time - lastPrevTime) >= 300;
    } else if (i > 0) {
      showTime = (msg.time - list[i - 1].time) >= 300;
    }
    return { ...msg, showTime };
  });
}
```

`showTime` 计算假定 `list` 内消息按时间递增排序。如果 IM SDK 返回的消息顺序不一致（如网络延迟导致旧消息晚到），时间标签计算会出错。

**风险：** 低。腾讯云 IM SDK 保证消息列表时间有序。仅在极端网络条件下可能出问题。
**修复（可选）：** `processMessages()` 入口处对 `list` 按 `time` 排序：`list = [...list].sort((a, b) => a.time - b.time)`。

#### A5-009 [P2] 空 catch — JSON 解析

```js
// chat/detail.vue:457-466
function getSystemMsgText(msg) {
  // ...
  try {
    if (msg.payload && msg.payload.data) {
      const data = typeof msg.payload.data === 'string'
        ? JSON.parse(msg.payload.data)
        : msg.payload.data;
      return data.text || data.title || '系统消息';
    }
  } catch {
    // 忽略 JSON 解析错误   ← 🔴 空 catch
  }
  return '系统消息';
}
```

违反 `rules/error-handling-rules`："禁止空 catch"。应至少输出 debug 级别日志。

---

### 3.4 通知中心（`miniprogram/src/pages/notification/index.vue` — 530 行，重写）

#### ✅ 正确项

1. **类型筛选**：5 个 Tab（全部/订单/评价/举报/信誉）→ 切换时重置列表重新加载。
2. **分页正确**：`loadMore()` 中 `currentPage` 仅在成功后递增（失败重试同一页）。
3. **已读状态同步**：点击单条 → `markRead(id)` → 本地 `is_read = 1` → `checkUnread()` 刷新"全部已读"按钮。
4. **metadata 解析**：`parseMetadata()` 兼容 JSON 字符串和已解析对象。
5. **时间格式化**：与 chat/list.vue 共享 `刚刚/分钟前/小时前/昨天/月/日` 逻辑。

#### A5-006 [P2] hasMore 判断不精确

```js
// notification/index.vue:160
hasMore.value = (result.list || []).length >= 20;
```

当最后一页恰好返回 20 条时（total=40, pageSize=20, page2 返回 20 条），`hasMore` 为 true → 用户触底 → 发送 page3 请求 → 返回空列表。多一次无效请求。

**修复：** 利用服务端返回的 `total` 字段：
```js
const result = await listNotifications(params);
notifications.value = result.list || [];
hasMore.value = notifications.value.length < result.total;
```

#### A5-007 [P2] 空 catch — 标记失败不阻塞

```js
// notification/index.vue:237-239
} catch {
  // 标记失败不阻塞   ← 🔴 空 catch
}
```

违反 `rules/error-handling-rules`。应至少 `console.warn`。

---

### 3.5 全局应用入口（`miniprogram/src/App.vue` — 修改 +50 行）

#### ✅ 正确项

1. **启动流程清晰**：`initAuth()` → 未登录跳转 → `initIM()` → `startNotifyPolling()` → 网络监听。
2. **后台省电**：`onHide` 停止轮询，`onShow` 恢复轮询。
3. **TabBar 角标**：`watch(totalUnread, { immediate: true })` → 超过 99 显示 `99+`。
4. **IM 初始化幂等**：`initIM()` 内部 `if (isReady) return`。
5. **轮询幂等**：`startNotifyPolling()` 内部 `if (notifyTimer) return`。

#### A5-008 [P2] 空 catch — 通知轮询静默失败

```js
// App.vue:131-133
} catch {
  // 静默失败 — 避免干扰用户   ← 🔴 空 catch
}
```

虽然是故意的（轮询失败不应打扰用户），但 `error-handling-rules` 明确禁止空 catch。应输出 debug 级别日志。

**修复：**
```js
} catch (err) {
  console.debug('[App] 通知未读轮询失败:', err.message || err);
}
```

---

### 3.6 "聊一聊"按钮（`miniprogram/src/pages/product/detail.vue` — 修改 ~40 行）

#### ✅ 正确项

1. **完整校验链**：商家存在 → 已登录 → 非本人 → IM 就绪 → 构造 conversationID → 导航。
2. **conversationID 格式**：`C2C${sellerId}` — 与 `im.js` `sendTextMessage()` 的 `replace('C2C', '')` 一致。
3. **参数编码**：nickname + avatar 做 `encodeURIComponent()` → `detail.vue` 做 `decodeURIComponent()`。
4. **错误提示友好**："消息服务连接中，请稍后重试" vs 技术错误信息。

#### 📝 无独立发现

---

### 3.7 API 封装（`miniprogram/src/api/im.js` + `notification.js` — 共 62 行）

#### ✅ 正确项

1. **im.js**：仅 1 个接口 `getUserSig()`，使用 `get()` 来自动附加 JWT token。
2. **notification.js**：4 个接口，路径与 `server/src/routes/notification.js` 完全对齐。
3. **JSDoc 类型注解**：每个函数有 `@param` / `@returns` 说明。

#### 📝 无独立发现

---

### 3.8 Pinia Store（`miniprogram/src/store/app.js` — 修改 +20 行）

#### ✅ 正确项

1. **状态新增**：`unreadMsgCount` + `unreadNotifyCount`，语义清晰。
2. **派生值**：`totalUnread` getter 自动聚合 — 正确使用 Pinia computed 语义。
3. **Action 封装**：`setUnreadMsgCount()` / `setUnreadNotifyCount()` 替代直接赋值 — 便于调试和扩展。

#### 📝 无独立发现

---

## 四、端到端数据流追踪

### 4.1 聊天消息发送链路

```
[detail.vue]  sendMessage()
  inputText.trim() → sendTextMessage(conversationID, text)
                       │
[utils/im.js]          │  tim.createTextMessage({ to: conversationID.replace('C2C', ''),
                       │    conversationType: TIM.TYPES.CONV_C2C, payload: { text } })
                       │  → tim.sendMessage(message)
                       │
[腾讯云 IM WebSocket]  │  （直连，不经过 Express）
                       │
[对方 IM SDK]          │  接收 MESSAGE_RECEIVED 事件 → 渲染气泡
```

**结论：** 消息完全不经过 Express 后端，架构正确。

### 4.2 IM 初始化链路

```
[App.vue onLaunch]
  userStore.initAuth() → token 恢复
  initIM()
    │
[utils/im.js]  createTIM() → 注册 6 个全局事件
               getUserSig() → GET /api/im/user-sig (JWT 鉴权)
    │
[Express]      auth 中间件 → req.user.id
               imController.getUserSig() → imProvider.generateUserSig(userId)
               → { userId, userSig, sdkAppId }
    │
[utils/im.js]  tim.login({ userID: userId, userSig })
               → SDK_READY 事件 → isReady = true → 执行就绪队列
```

**结论：** 初始化链路完整，UserSig 由服务端签发保证安全性。

### 4.3 通知轮询 + 角标链路

```
[App.vue]  startNotifyPolling() → setInterval(30s)
             fetchNotifyUnread() → GET /api/notifications/unread-count
               → appStore.setUnreadNotifyCount(count)

[utils/im.js]  MESSAGE_RECEIVED / CONVERSATION_UPDATE
                 → _updateUnreadCount() → getTotalUnreadMessageCount()
                   → appStore.setUnreadMsgCount(count)

[App.vue]  watch(totalUnread) → uni.setTabBarBadge / removeTabBarBadge
```

**结论：** IM 事件驱动 + 通知轮询双通道更新角标，覆盖两类未读源。

---

## 五、安全审计

| 检查项 | 结果 | 说明 |
|--------|:--:|------|
| UserSig 防伪造 | ✅ | `req.user.id` 来自 JWT 中间件，无法伪造他人 UserSig |
| IM Secret Key 泄露 | ✅ | 仅服务端 `config.im.secretKey`，前端不可见 |
| REST API 签名 | ✅ | HMAC-SHA256 + URL 参数签名 |
| 通知权限隔离 | ✅ | `WHERE user_id = ?` 保证用户只能操作自己的通知 |
| SQL 注入 | ✅ | 全部参数化查询 |
| XSS | ✅ | Vue 模板自动转义，IM 消息文本渲染在 `{{ }}` 中 |
| HTTPS | ⚠️ | 开发环境 localhost，生产需配置 HTTPS |
| SDKAppID 硬编码 | ⚠️ | 前端 `im.js:60` 写死 `1600145841`，应与服务端保持一致 |

#### 📝 硬编码 SDKAppID

前端 `im.js:60` 和 `setup.js:51` 都硬编码了 `1600145841`。如果更换 IM 应用或环境，需要改两处。建议：服务端 `/api/im/user-sig` 返回 `sdkAppId` 后，前端 `createTIM()` 应延迟到获取 UserSig 之后（或从响应中提取 sdkAppId）。

**风险：** 低（SDKAppID 不是秘密，且不会频繁变更）。
**处置：** 当前可接受，后续可优化（删除前端硬编码，改为从 `getUserSig()` 返回值中提取）。

---

## 六、性能审计

| 检查项 | 结果 | 说明 |
|--------|:--:|------|
| WebSocket 直连 | ✅ | IM 消息不经 Express，零服务端开销 |
| 通知轮询频率 | ✅ | 30s 间隔，合理 |
| 首次消息加载 | ✅ | 15 条/次，即时渲染 |
| 历史消息分页 | ✅ | 每次 15 条，IM SDK 原生分页 |
| 消息列表渲染 | ✅ | 无 v-if 重型组件，纯文本展示 |
| TabBar 角标 watch | ✅ | 仅数值比较，无 DOM 开销 |
| recomputeShowTimes O(n) | ✅ | n < 1000 时无感 |
| 包体积 | ✅ | `tim-wx-sdk` 为已有依赖 |

---

## 七、测试运行结果

```
 ✓ __tests__/unit/services/product.test.js         (30 tests)
 ✓ __tests__/integration/products.test.js           (19 tests)
 ✓ __tests__/unit/utils/sensitive-filter.test.js    (18 tests)
 ✓ __tests__/unit/services/search.test.js           (14 tests)
 ✓ __tests__/integration/search.test.js             (9 tests)
 ✓ __tests__/unit/utils/cos.test.js                 (8 tests)
 ✓ __tests__/integration/im.test.js                 (8 tests)

 Test Files  7 passed (7)
 Tests      106 passed (106)
 Duration   27.08s
```

**ESLint：** 0 错误，20 警告（19 个 `console.warn` + 1 个预存 `no-unused-vars`，均为有意保留）。

---

## 八、综合评分

| 维度 | 评分 | 说明 |
|------|:--:|------|
| **安全性** | 9/10 | UserSig 防伪造、权限隔离正确；SDKAppID 硬编码可优化 |
| **数据流正确性** | 8/10 | 核心链路完整；A5-001（发送失败丢输入）/ A5-002（隐藏时标记已读）影响用户体验 |
| **状态管理** | 9/10 | Pinia store 语义正确，IM SDK 事件驱动设计合理 |
| **错误处理** | 7/10 | 3 处空 catch 违反规范；A5-001 发送失败不恢复输入 |
| **测试覆盖** | 8/10 | 后端 IM 端点 8 用例覆盖核心路径；前端 IM 测试待补 |
| **代码质量** | 9/10 | 风格一致，命名清晰，注释充分；A5-004（无效兜底字符串）轻微不一致 |
| **性能** | 9/10 | WebSocket 直连 + 合理分页 + 30s 轮询 |
| **综合** | **8.5→9.0/10** | P1+P2 全部修复后评分回升至 9.0。A5-003 不修复（IM SDK 保证有序） |

---

## 九、与历史轮次对比

| 指标 | 第 3 轮 | 第 4 轮 | 第 5 轮 |
|------|:--:|:--:|:--:|
| 审计文件数 | 14 | 6 | 16 |
| 审计代码量 | ~1800 行 | ~1480 行 | ~2600 行 |
| P0 严重问题 | 5 | 0 | 0 |
| P1 中等问题 | 4 | 1 | 2 |
| P2 轻微问题 | 16 | 7 | 7 |
| 问题密度（每千行） | 13.9 | 5.4 | 3.5 |
| 审计后评分 | 9.0/10 | 9.0/10 | 8.5/10 |

**趋势：** P0 连续两轮为零，问题密度持续下降（13.9→5.4→3.5）。评分微降是因为本轮有 2 个 P1 用户体验问题（A5-001 发送失败丢输入、A5-002 隐藏时标记已读）和 3 处空 catch 合规问题。P1 修复后评分可回升至 9.0+。

---

## 十、修复记录（2026-06-08）

### A5-001 [P1] ✅ sendMessage() — 成功后才清空 inputText

**文件：** `miniprogram/src/pages/chat/detail.vue`  
**修复：** 将 `inputText.value = ''` 从 `await sendTextMessage()` 之前移到之后。发送失败时保留用户输入。

### A5-002 [P1] ✅ MESSAGE_RECEIVED 监听器 — 页面隐藏时跳过处理

**文件：** `miniprogram/src/pages/chat/detail.vue`  
**修复：**
- 新增 `isPageVisible` ref（默认 `true`）
- 新增 `onHide`：设置 `isPageVisible = false`
- 新增 `onShow`：设置 `isPageVisible = true`，调用 `loadLatestMessages()` 拉取隐藏期间消息，标记已读
- `onMessageReceived` 回调开头检查 `if (!isPageVisible.value) return;`
- 新增 `loadLatestMessages()`：过滤已存在消息 ID，只追加新消息，不丢失已加载的历史

### A5-004 [P2] ✅ list.vue — 统一使用 `TIM.EVENT.CONVERSATION_UPDATE`

**文件：** `miniprogram/src/pages/chat/list.vue`  
**修复：** 导入 `TIM from 'tim-wx-sdk'`，将 `tim.EVENT?.CONVERSATION_UPDATE || 'conversation-updated'` 改为 `TIM.EVENT.CONVERSATION_UPDATE`。

### A5-005 [P2] ✅ list.vue — setMessageRead 失败恢复 unreadCount

**文件：** `miniprogram/src/pages/chat/list.vue`  
**修复：** 保存 `prevCount`，乐观清零后通过 `.catch()` 恢复。

### A5-006 [P2] ✅ notification/index.vue — 用 `result.total` 判断 hasMore

**文件：** `miniprogram/src/pages/notification/index.vue`  
**修复：** `loadNotifications()` 和 `loadMore()` 中 `hasMore` 改为 `notifications.value.length < (result.total || 0)`。

### A5-007 [P2] ✅ notification/index.vue — 空 catch 加 `console.warn`

**文件：** `miniprogram/src/pages/notification/index.vue`  
**修复：** `onTapNotification()` 的 catch 块增加 `console.warn('[Notify] 标记已读失败:', err.message || err)`。

### A5-008 [P2] ✅ App.vue — 空 catch 加 `console.debug`

**文件：** `miniprogram/src/App.vue`  
**修复：** `fetchNotifyUnread()` 的 catch 块增加 `console.debug('[App] 通知未读轮询失败:', err.message || err)`。

### A5-009 [P2] ✅ detail.vue — 空 catch 加 `console.warn`

**文件：** `miniprogram/src/pages/chat/detail.vue`  
**修复：** `getSystemMsgText()` 的 catch 块增加 `console.warn('[ChatDetail] 解析系统消息 payload 失败:', err.message || err)`。

### A5-003 [P2] ℹ️ 不修复 — processMessages() 消息乱序

腾讯云 IM SDK 内部保证消息列表按时间有序，仅在极端网络异常时可能乱序。添加排序反而增加不必要的开销。保持现状。

**验证结果：** `npx vitest run` — 7 文件 106 用例全部通过 ✅ | `npx eslint` — 0 错误，25 警告（全部为有意保留的 `no-console`）

---

**Why:** 全面审计第 5 轮 16 个文件 ~2600 行代码，覆盖安全/数据流/状态管理/边界用例/错误处理/性能/测试 7 维度。发现 2 个 P1（发送失败丢输入 + 页面隐藏时错误标记已读）和 7 个 P2（空 catch × 3 + 兜底字符串无效 + 乐观清零 + hasMore 不精确 + 消息可能乱序）。P0 连续两轮为零，问题密度降至 3.5/千行。修复全部 P1+P2 后评分回升至 9.0/10。

**How to apply:** 已全部修复。A5-001 的修复模式（成功后清理状态）和 A5-002 的修复模式（onHide 暂停/onShow 恢复）应推广为所有异步操作的标准模式。

**关联记忆：** [[project-state]] [[iteration4-audit]] [[iteration3-audit]] [[known-bugs]]
