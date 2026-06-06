# 版本管理体系

> 来源：完整开发.md §4.3 — AI 系统是"漂移"的，所有 AI 资产必须版本化。

---

## 版本化范围

| 资产类型 | 存储位置 | 版本格式 | 变更记录要求 |
|----------|---------|---------|-------------|
| **Prompt 模板** | `prompts/` | `v<MAJOR>.<MINOR>` 文件头部 YAML | 记录修改原因 + 效果对比 |
| **Workflow 脚本** | `.claude/workflows/` | `version` 字段在 `meta` 块中 | 记录触发条件变更 + 输出差异 |
| **Skill 定义** | `.claude/skills/` | YAML frontmatter `version` | 记录知识/流程变更 |
| **Eval 数据集** | `evals/` | 目录名含日期 `YYYY-MM-DD` | 记录新增/删除/修改的 Case |
| **CLAUDE.md** | 根目录 | Git 历史 | 每次修改在 commit message 中说明原因 |
| **rules/** | `rules/` | Git 历史 | 规范变更需记录影响范围 |
| **文档 (docs/)** | `docs/` | 文件内 `updated` 日期 | CHANGELOG.md |

---

## 版本号规则

```
v<MAJOR>.<MINOR>

MAJOR: 不兼容的变更（输出格式变化、接口变化）
MINOR: 向后兼容的改进（措辞优化、边界 Case 补充）
```

示例：`v1.0` → `v1.1`（优化了 Prompt 措辞）→ `v2.0`（改变了输出 Schema）

---

## CHANGELOG 联动

所有 AI 资产变更汇总到 `docs/CHANGELOG.md`。格式：

```markdown
### Changed — Prompt 优化（YYYY-MM-DD）
- **`prompts/code-review.md` v1.0 → v1.1**：增加文件行数检查规则
- **原因**：审查遗漏了 >500 行的文件
```

---

## 回滚策略

1. Git 历史保留所有版本 → `git log -- <path>` 查看变更
2. 通过 Git tag 标记稳定版本组合（如 `v1.0.0-stable-prompts`）
3. Prompt/Workflow/Skill 回滚：`git checkout <commit> -- <file>`
