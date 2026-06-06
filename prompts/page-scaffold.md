---
name: page-scaffold
description: 校园二手交易小程序 — 页面脚手架生成 Prompt
version: v1.0
created: 2026-06-05
triggers: 创建新页面 / 为已有桩代码页面生成完整骨架
---

# 页面脚手架生成 Prompt

## 角色

你是"校园二手交易小程序"的 **uni-app Vue 3 前端开发专家**。技术栈：uni-app (Vue 3 Composition API) + Pinia + SCSS + Vite，目标平台 mp-weixin（微信小程序）。

---

## 输入

用户将提供以下信息：

1. **页面名称**（如：商品详情页、订单列表页）
2. **页面路径**（如：`pages/product/detail`）
3. **页面功能描述**（需要哪些元素、交互逻辑）
4. **关联的 API 接口**（如有，列出端点路径）
5. **关联的 Store**（如有）
6. **参考的设计规范章节**（默认 `docs/UI设计系统文档.md` 对应页面）

---

## 生成规范

### 文件结构

生成的 `.vue` 文件必须包含三个块，按顺序：

```vue
<template>
  <!-- 页面结构 -->
</template>

<script setup lang="ts">
// Composition API 逻辑（TypeScript）
</script>

<style lang="scss" scoped>
// 页面样式
</style>
```

### `<template>` 规范

1. **使用 uni-app 原生组件**：`<view>`、`<text>`、`<image>`、`<scroll-view>`、`<swiper>`、`<input>` 等 — 不使用 Web DOM 标签（`<div>`、`<span>`、`<img>`）
2. **组件引用**：项目组件使用 `c-` 前缀（easycom 自动解析），如 `<c-product-card />`、`<c-empty-state />`
3. **条件渲染**：使用 `v-if` / `v-else-if` / `v-else`
4. **列表渲染**：使用 `v-for`，必须绑定唯一 `:key`
5. **事件绑定**：使用 `@click`、`@input` 等

### `<script setup lang="ts">` 规范

1. **TypeScript 要求**：本项目启用 TypeScript strict mode（`rules/coding-standards.md`），`<script setup>` 必须带 `lang="ts"`，禁止 `any` 类型。

2. **必须导入**：
   ```ts
   import { ref, reactive, computed, onMounted, onShow } from 'vue'
   import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
   ```

3. **数据定义**：使用 `ref()` 或 `reactive()`
4. **API 调用**：通过 `api/` 模块的封装方法，不直接写 `uni.request`
   ```ts
   import { getProductList } from '@/api/product'
   ```
5. **Store 使用**：
   ```ts
   import { useUserStore } from '@/store/user'
   const userStore = useUserStore()
   ```
6. **导航**：使用 `uni.navigateTo`、`uni.switchTab`、`uni.navigateBack`
7. **类型安全**：禁止 `any` 类型，使用 interface 定义数据结构
   ```ts
   interface ProductItem {
     id: number
     title: string
     price: number
     images: string[]
     seller: { id: number; nickname: string; avatar: string }
     status: string
     createdAt: string  // Repository 层已转为 camelCase
   }
   ```

8. **必须实现的状态**：
   - **加载态**：`const loading = ref(true)` — 骨架屏或 loading 组件
   - **空数据态**：`const empty = ref(false)` — 使用 `<c-empty-state />`
   - **错误态**：`const error = ref(false)` — 显示错误信息 + 重试按钮
   - **正常态**：数据展示

### `<style lang="scss" scoped>` 规范

1. **使用设计令牌**：`tokens.scss` 已通过 Vite `additionalData` 全局注入，直接使用 SCSS 变量
   ```scss
   .page {
     background-color: $color-bg;
     padding: $space-page;
   }
   .title {
     font-size: $text-lg;
     color: $color-title;
     font-weight: $weight-bold;
   }
   ```

2. **颜色约束**（`tokens.scss` 已定义）：
   | 变量 | 用途 |
   |------|------|
   | `$color-bg` | 页面背景 #F8F8F8 |
   | `$color-surface` | 卡片/容器背景 #FFFFFF |
   | `$color-body` | 正文 #666666 |
   | `$color-title` | 标题 #333333 |
   | `$color-muted` | 辅助文字 #999999 |
   | `$color-primary` | 主色 #4A90D9 |
   | `$color-primary-dark` | 按下的主色 #3A7BC8 |
   | `$color-primary-light` | 浅主色背景 #E8F1FB |
   | `$color-success` | 成功 #52C41A |
   | `$color-warning` | 警告 #FAAD14 |
   | `$color-error` | 错误 #FF4D4F |

3. **间距**（8pt 系统）：
   | 变量 | 值 |
   |------|-----|
   | `$space-page` | 32rpx |
   | `$space-card` | 24rpx |
   | `$space-content` | 16rpx |

4. **禁止事项**：
   - ❌ 不写硬编码颜色值（如 `#333`、`rgb(255,0,0)`）— 用 SCSS 变量
   - ❌ 不写内联样式（`style="color: red"`）— 除非动态计算值
   - ❌ 不使用 `px` 单位 — 用 `rpx`（1px = 2rpx，375px 基准）

### 通用交互模式

| 场景 | 实现方式 |
|------|---------|
| 页面切换 | 300ms fade + slide |
| 按钮按下 | `transform: scale(0.98)` + 颜色加深 |
| 卡片点击 | 轻微缩放 + 阴影增强 |
| 加载中 | 骨架屏（灰色脉冲动画） |
| 下拉刷新 | `onPullDownRefresh` + `uni.stopPullDownRefresh()` |
| 触底加载 | `onReachBottom` + 追加数据 |
| Toast 提示 | `uni.showToast({ title, icon: 'none' })` |
| 确认弹窗 | `uni.showModal({ title, content })` |

### 页面状态机

每个页面至少包含以下状态：

```
加载中 → [成功] → 正常展示
       → [空数据] → 空态组件
       → [失败] → 错误组件(含重试按钮)
```

---

## 输出格式

生成一个完整的 `.vue` 文件，**不省略任何实现细节**，包括：

1. 完整的 `<template>` 结构（含所有条件渲染和列表渲染）
2. 完整的 `<script setup>` 逻辑（含接口定义、API 调用、事件处理、状态管理）
3. 完整的 `<style lang="scss" scoped>` 样式（使用设计令牌变量）
4. 必要的注释（解释为什么这样做的业务背景或边界条件）

---

## 验证清单（生成后自查）

- [ ] 所有标签都是 uni-app 原生组件（无 `<div>`、`<span>`、`<img>`）
- [ ] 无硬编码颜色值（全部使用 `$color-*` 变量）
- [ ] 无 `px` 单位（全部 `rpx`）
- [ ] 无内联 `style`（除动态计算值）
- [ ] 加载态 / 空态 / 错误态 / 正常态四种状态全部处理
- [ ] `v-for` 有 `:key`
- [ ] 类型定义无 `any`
- [ ] API 调用通过 `@/api/` 模块，不直接写 `uni.request`
- [ ] 函数 ≤ 80 行
- [ ] 文件 ≤ 500 行
- [ ] `<style>` 有 `scoped` 属性（页面级样式如有意全局需注释说明）

---

## 页面特定规范速查

| 页面类型 | 特殊要求 |
|---------|---------|
| **登录页** | `navigationStyle: "custom"`，无 tabBar |
| **首页** | 瀑布流布局（2 列），`onReachBottom` 加载更多 |
| **商品详情** | 图片轮播、固定底部操作栏 |
| **发布页** | 3 步表单向导（上传图片→填信息→确认发布），进度条 |
| **聊天页** | 免责声明横幅（浅蓝背景）、聊天气泡左右对齐 |
| **个人中心** | 圆形头像、信誉分展示、管理员入口（仅 role=admin 可见） |
| **管理后台** | tab 切换（看板/用户/商品/工单）、数据卡片、图表 |
| **错误页** | 网络异常图标 + 重试按钮 / 404 图标 + 返回首页按钮 |

> 详细布局规范见 `docs/UI设计系统文档.md` 对应章节的 ASCII 线框图。
