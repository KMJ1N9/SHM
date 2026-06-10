## 项目状态与进度

- [项目当前状态](project-state.md) — 第 1~7 轮全部完成 ✅，测试 9 文件 126 用例全部通过
- [第 2 轮审阅报告](iteration2-review.md) — 注册/登录：9 文件，P0×1 + P1×1 已修复
- [第 3 轮审阅报告](iteration3-review.md) — 商品发布/浏览：14 文件，P0×2 + P1×2 + P2×3 全部已修复
- [第 3 轮全面审计](iteration3-audit.md) — 14 文件逐行审计：P0×0 / P1×4 / P2×5 / P3×4，P1+P2 全部已修复 ✅，综合评分 9.0/10
- [第 4 轮全面审计](iteration4-audit.md) — 6 文件 ~1480 行 6 维度审计：P0×0 / P1×1 / P2×7，已修复 5 项 ✅，综合评分 9.0/10
- [第 5 轮全面审计](iteration5-audit.md) — 16 文件 ~2600 行 7 维度审计：P0×0 / P1×2 / P2×7，综合评分 8.5/10（P1 修复后 9.0+）
- [第 6 轮编码计划](../plans/iteration6-order-trade.md) — 交易流程（订单+评价），包含科学混合并行策略（4 波 6 Agent + 1 Workflow，预估 3.7h，加速 2.3×）
- [第 6 轮全面审计](iteration6-audit.md) — 15 文件 ~3200 行 7 维度审计：P0×0 / P1×2 / P2×7，综合评分 8.5/10
- [第 7 轮全面审计](iteration7-audit.md) — 11 文件 ~1550 行 7 维度审计：P0×0 / P1×4 / P2×5，综合评分 8.5/10
- [已知 Bug](known-bugs.md) — BUG-001~030 全部已修复 ✅（第 6 轮新增 BUG-025~030），BUG-014 待修复（顽固缓存，非代码问题）

## 文档审阅（7 轮，累计 95 个问题，综合 8.8/10）

- [7 轮文档审阅全记录](doc-audit-rounds.md) — 汇总 + R7 未修复项（6 项 P1/P2 不阻塞编码）
- [文档清单](document-inventory.md) — docs/ 下 16 份已生成文档的用途和状态索引

## 工程规范与基础设施

- [编码规范核心约束](rules-summary.md) — rules/ 14 份规范红线速查表
- [Skills 技能包安装](skills-setup.md) — 6 包 540+ skill 四级管理 + 场景路由 + 技术栈红线
- [迁移系统](migration-system.md) — JS 编程式迁移（5 个迁移全部就位），legacy .sql 文件应忽略
