# API 规范

## RESTful 风格

遵循 RESTful API 设计规范。

### 正确示例

```txt
GET    /users
GET    /users/:id
POST   /users
PUT    /users/:id
DELETE /users/:id
```

### 错误示例

```txt
/getUser
/updateUserInfo
/deleteUserById
```

## 返回结构

所有 API 必须返回统一格式：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

## 禁止行为

禁止返回格式不统一的响应。
