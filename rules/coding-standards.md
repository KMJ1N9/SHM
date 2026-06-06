# 编码规范

## 命名规范

### 变量

使用 camelCase 命名法。

禁止：

```txt
user_name
aaa
tmp
data2
```

### 类名

使用 PascalCase 命名法。

示例：

```txt
UserService
OrderController
AuthMiddleware
```

### 常量

使用 UPPER_CASE 命名法。

### 文件命名

前端：kebab-case
后端：依据语言规范

## TypeScript 规范

必须：

- 开启 strict mode
- 禁止 any
- 优先 interface
- 明确返回值类型
- 避免类型断言滥用

禁止：

```ts
const data: any = xxx
```

推荐：

```ts
interface UserInfo {
  id: number
  name: string
}
```

## 开发原则

遵循以下工程原则：

- SOLID
- DRY
- KISS
- Separation of Concerns
- Single Responsibility
- High Cohesion
- Low Coupling

必须：

- 保持模块边界清晰
- 保持职责单一
- 保持可测试性
- 保持可维护性
