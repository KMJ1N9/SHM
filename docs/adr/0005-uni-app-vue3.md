# ADR-0005：uni-app (Vue 3) 跨端框架（非原生微信小程序）

- **日期：** 2026-06-04
- **状态：** 已采纳
- **决策者：** 单人开发

## 背景

校园二手交易平台首要目标是微信小程序，但需要为未来可能的 H5 版本留有余地。单人开发，需要选择前端技术方案。

## 决策

**uni-app (Vue 3) + Vite + Pinia，编译目标 mp-weixin（微信小程序）。**

- 使用 HBuilder X 或 Vite 插件开发
- 组件/页面用 Vue 3 Composition API 编写
- 状态管理使用 Pinia
- 样式使用 SCSS + 设计令牌（tokens.scss）
- 编译为微信小程序原生代码运行

## 考虑的替代方案

| 方案 | 优点 | 缺点 | 为何未选 |
|------|------|------|---------|
| 原生微信小程序 | 最轻量、性能最好、API 最新 | 语法封闭（WXML/WXSS/JS）、无法跨端、生态工具少、无 TypeScript 原生支持 | 开发体验差，未来做 H5 需完全重写 |
| Taro (React) | 跨端、React 生态 | 个人对 React 不如 Vue 熟悉，Taro 编译层可能引入兼容问题 | 技术栈不匹配 |
| uni-app (Vue 2) | 成熟稳定 | Vue 2 已停止维护，Composition API 需额外插件 | 技术负债——不应在新项目中使用已停维护的版本 |
| 微信小程序 + 独立 H5 项目 | 两端各自最优 | 双倍开发量，单人不可行 | 不现实 |

## 影响范围

- `miniprogram/` — 全部前端代码（15 页面 + 6 组件 + API + Store + Utils）
- `miniprogram/vite.config.js` — Vite + uni-app 编译插件
- `miniprogram/tsconfig.json` — TypeScript 配置
- `miniprogram/styles/tokens.scss` — 设计令牌全局注入
- 构建输出 → 微信开发者工具运行

## 后果

### 正面
- Vue 3 Composition API — 逻辑复用方便（hooks）
- Pinia — 相较 Vuex 更轻量、TS 友好
- 一套代码可编译为微信小程序 / H5 / App（未来扩展成本低）
- 社区生态成熟，uni-app 插件市场丰富
- SCSS + 全局令牌注入 — 样式统一管控

### 负面
- **uni-app 3.0 为 alpha 版本**，API 可能变动，依赖不稳定
- 编译层可能引入与原生小程序的差异（部分 wx API 需通过 uni.* 调用）
- 包体积大于原生小程序
- 调试链路长（Vue → 编译 → 原生代码 → 微信开发者工具）
- HBuilder X 与 Vite 双工具链存在摩擦

### 缓解措施
- 锁定依赖精确版本（package.json 使用 exact version，不用 `^`）
- 优先使用 uni-app 标准 API，避免直接调用 wx.*（除非 uni.* 不支持）
- 定期关注 uni-app 3.0 stable 发布

## 相关

- 关联文档: [技术架构文档 §三](../技术架构文档.md)
- 关联文档: [UI设计系统文档](../UI设计系统文档.md)
- 关联文档: [tech-debt.md](../tech-debt.md) TD-001（uni-app alpha 版本风险）
