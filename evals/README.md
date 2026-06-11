# evals/ — AI 输出质量评估框架

> 校园二手交易小程序 — Claude Code 辅助开发质量保证数据集。

---

## 快速开始

```bash
# 1. 选择一个评估场景
#    模型切换？     → 运行 prompts/ 全部 6 个 Case
#    新 feature？   → 运行 golden/ 中对应 Case
#    Bug 修复后？   → 运行 regression/ 中对应 Bug 模式的 Case
#    边界审查？     → 运行 edge-cases/ 中相关 Case

# 2. 在 Claude Code 会话中提交 Prompt
# 3. 对照评估维度检查输出
# 4. 记录结果到对应 README 的评估记录表中
```

---

## 目录结构

```
evals/
├── README.md                   ← 本文件（框架总览 + 使用方法）
├── golden/                     ← [Golden Dataset](golden/README.md)（8 个核心功能黄金标准）
├── edge-cases/                 ← [Edge Cases](edge-cases/README.md)（8 个边界场景）
├── regression/                 ← [Regression](regression/README.md)（12 个已知 Bug 回归 Case）
└── prompts/                    ← [Prompts](prompts/README.md)（6 个标准 Prompt 评估 Case）
```

---

## 四个数据集的关系

```
          ┌──────────────┐
          │   Prompts    │  ← 标准 Prompt 模板 + 评估矩阵
          │   (6 Case)   │     用于对比模型/评估 Prompt 稳定性
          └──────┬───────┘
                 │ 用 Prompts 测试 AI 能否生成符合规范的代码
                 ▼
    ┌────────────────────────────┐
    │  Golden  │  Edge Cases     │  ← 生成代码的"正确性"评估
    │ (8 Case) │  (8 Case)       │     Golden = 正常流程 / Edge = 异常流程
    └─────────┴─────────────────┘
                 │ 发现新的 Bug 模式
                 ▼
          ┌──────────────┐
          │  Regression  │  ← 已知 Bug 模式的回归防护
          │  (12 Case)   │     每个 Case 对应 1+ 个真实 Bug
          └──────────────┘
```

---

## 使用场景

| 场景 | 使用哪个数据集 | 频率 |
|------|:----------|:--:|
| **模型切换**（如 Opus → Sonnet） | prompts/ 全部 6 Case | 每次切换 |
| **修改 CLAUDE.md / rules/** | prompts/ + golden/ 各选 3 Case | 每次修改 |
| **新 feature 开发前** — 确认 AI 理解规范 | golden/ 对应技术的 Case | 按需 |
| **Code Review 不通过** — 检查是否已知 Bug 模式 | regression/ 对应 Case | 按需 |
| **发版前最终审计** | regression/ 全部 12 Case | 每个 milestone |
| **发现新 Bug** → 提取为回归 Case | regression/ 新增 Case | 每次发现 |
| **边界条件检查** | edge-cases/ 相关 Case | 每个 feature |

---

## 评估维度（8 维）

| 维度 | 检查项 | 依据 | 缩写 |
|------|--------|------|:--:|
| 文件规范 | 单文件 ≤ 500 行，超过必须拆分 | [file-rules.md](../rules/file-rules.md) | FILE |
| 函数规范 | 单函数 ≤ 80 行、参数 ≤ 5 个、单一职责 | [function-rules.md](../rules/function-rules.md) | FUNC |
| 命名规范 | camelCase / PascalCase / UPPER_CASE / kebab-case / snake_case | [coding-standards.md](../rules/coding-standards.md) | NAME |
| API 规范 | RESTful + 统一 `{code, message, data}` 格式 | [api-rules.md](../rules/api-rules.md) | API |
| 安全规范 | 输入校验、参数化查询、无硬编码密钥、JWT 鉴权 | [security-rules.md](../rules/security-rules.md) | SEC |
| 错误处理 | 无空 catch、AppError 工厂函数、含 traceId、winston 日志 | [error-handling-rules.md](../rules/error-handling-rules.md) | ERR |
| 数据库规范 | 无 `SELECT *`、参数化查询、分页含 LIMIT/OFFSET、无 N+1 | [database-rules.md](../rules/database-rules.md) | DB |
| UI 规范 | tokens.scss 设计令牌、8pt 间距、SCSS class、无内联样式泛滥 | [ui-rules.md](../rules/ui-rules.md) | UI |

---

## 评估流程

### 标准流程

```
1. 选择 Case
   └→ 从对应数据集 README 中选择评估 Case

2. 提交 Prompt
   └→ 在 Claude Code 会话中粘贴 Prompt，观察输出

3. 检查输出（对照评估维度）
   ├→ API 层：5 层分离？统一响应格式？参数化 SQL？
   ├→ DB 层：无 SELECT *？LIMIT/OFFSET 参数化？字段完整？
   ├→ SEC 层：参数化查询？无硬编码密钥？输入校验？
   ├→ ERR 层：AppError 工厂？无空 catch？winston 日志？
   ├→ UI 层：tokens.scss 变量？8pt 间距？SCSS class？
   ├→ NAME 层：字段名一致性？命名风格正确？
   └→ FILE/FUNC 层：文件 ≤ 500 行？函数 ≤ 80 行？

4. 记录结果
   └→ 在数据集 README 的评估记录表中追加一行
      格式：日期 | 模型 | Case1 | Case2 | ... | 通过率
```

### 判定标准

| 标记 | 含义 | 说明 |
|:--:|------|------|
| ✅ | 通过 | 无违规 |
| ⚠️ | 部分通过 | 有 minor 违规（如缺 1 个日志、1 处命名不一致） |
| ❌ | 失败 | 有 major 违规（如 SQL 注入、空 catch、字段名完全不对） |
| N/A | 不适用 | 该 Case 不涉及此维度 |

---

## 数据集统计

| 数据集 | Case 数 | 覆盖维度 | 严重程度分布 |
|--------|:--:|------|:--:|
| [golden/](golden/README.md) | 8 | API/DB/SEC/ERR/UI/NAME/FUNC/FILE | critical×5, high×3 |
| [edge-cases/](edge-cases/README.md) | 8 | 全部 | critical×3, high×3, medium×2 |
| [regression/](regression/README.md) | 12 | 全部（重点：DB/API/SEC） | critical×8, high×4 |
| [prompts/](prompts/README.md) | 6 | 全部 | critical×4, high×2 |
| **合计** | **34** | — | **critical×20, high×12, medium×2** |

---

## 贡献新 Case

### 发现 AI 产出有 Bug 模式时

1. 确认是否为新的模式（查 `regression/README.md` 中是否已有类似 Case）
2. 在 `regression/README.md` 中新增一个 REG-XXX Case
3. 格式：`场景描述 → 输入 Prompt → 预期行为 → 违规示例`
4. 标注来源 Bug 编号

### 发现现有 Case 覆盖不足时

1. 在对应数据集的 README 中新增 Case
2. 更新本文件的统计表
3. 更新 `memory/known-bugs.md` 如果是新 Bug 模式

---

## 相关文件

- [CLAUDE.md](../CLAUDE.md) — 项目总纲 + 6 Skills 包场景路由 + 行为准则
- [rules/README.md](../rules/README.md) — 14 份编码规范核心约束
- [memory/known-bugs.md](../memory/known-bugs.md) — 33+ 已知 Bug 记录（Regression Case 来源）
- [memory/project-state.md](../memory/project-state.md) — 项目开发进度
- [docs/测试计划.md](../docs/测试计划.md) — 134 条测试用例
