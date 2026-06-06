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
