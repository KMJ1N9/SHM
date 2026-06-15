# 数据库规范

## 表命名规范

使用 snake_case 命名法。

示例：

```txt
user_info
order_record
product_category
chat_message
```

## 字段规范

所有表必须包含：

```txt
id
created_at
updated_at
```

**例外：** 以下类型的表可省略 `updated_at`（这些表只追加不修改）：
- 评价/评分表（reviews）— 评价一旦写入不可修改
- 审计日志表（admin_logs）— 审计记录不可篡改
- 事件日志表（user_events）— 事件记录不可变
- 归档表（*_archive）— 历史快照，由 `archived_at` 替代
- 关联弱实体表（product_images / report_evidence）— 仅作为父表的附属集合

`notifications` 表必须包含 `updated_at`（因为 `is_read` 状态会变更）。

## 查询规范

### 禁止行为

- SELECT *
- 无索引查询
- N+1 查询
- 在循环内频繁查询数据库

### 推荐做法

- 明确指定查询字段
- 使用 JOIN 替代循环查询
- 添加适当索引
- 使用分页查询
