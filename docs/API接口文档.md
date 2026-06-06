# API 接口文档：校园二手交易小程序

**版本：** v1.0
**基准域名：** `https://api.campus-market.example.com/api`
**最后更新：** 2026-06-04
**上游来源：** [技术架构文档 §五](技术架构文档.md)

---

## 一、通用约定

| 约定 | 说明 |
|------|------|
| Base URL | `https://api.campus-market.example.com/api` |
| 鉴权 | Header `Authorization: Bearer <JWT>`（除登录/注册、健康检查外均需携带） |
| 成功响应 | `{ code: 0, data: {...}, message: "ok" }` |
| 错误响应 | `{ code: <错误码>, message: "<人类可读描述>", detail?: <调试信息> }`。`detail` 仅在 development 环境返回，production 环境置 `null` |
| 错误码 | `0` 成功；`1xxx` 认证与授权；`2xxx` 资源；`3xxx` 业务状态冲突；`4xxx` 输入与风控；`5xxx` 权限；`6xxx` 系统与第三方 |
| 分页 | 偏移分页：Query `?page=1&pageSize=20`，返回 `{ list: [...], total: N, page: 1, pageSize: 20 }`。后期商品量增长后瀑布流"加载更多"切换为游标分页 `?cursor=id&limit=20` |

---

## 二、接口清单

> **角色权限矩阵：** `admin` = 全部权限；`cs`（客服）= 可处理工单，不可封号、不可看数据看板；`user` = 仅操作本人资源。

---

### 2.1 认证模块 `/api/auth`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| POST | `/api/auth/login` | 微信手机号授权登录（注册/登录合一） | — |
| POST | `/api/auth/refresh` | 刷新 access_token | refresh_token |
| GET | `/api/auth/me` | 获取当前登录用户信息 | access_token |

#### POST `/api/auth/login`

> 小程序通过 `<button open-type="getPhoneNumber">` 获取 `code` 后调用。后端调用微信 API 解密手机号，以手机号作为唯一标识——存在则登录，不存在则自动注册。

```
Request:
  Body: {
    code: "wx_phone_code_xxx"       // 微信 getPhoneNumber 返回的 code（一次性有效）
  }

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      access_token:  "eyJhbG...",   // JWT，7 天有效，payload 含 { sub, role, version }
      refresh_token: "eyJhbG...",   // JWT，30 天有效，payload 含 { sub, type: "refresh", version }
      is_new_user:   false,         // true = 本次自动注册
      user: {
        id:            1,
        phone:         "138****1234",
        nickname:      "微信用户",
        avatar:        "https://...",
        class_name:    "",
        dorm_building: "",
        role:          "user",
        status:        "active",
        credit_score:  100,
        created_at:    "2026-06-03T10:00:00.000Z"
      }
    }
  }

可能的错误:
  - 4001: code 参数缺失
  - 6003: 微信 API 调用失败（code 过期/无效）
  - 6999: 服务内部错误
```

#### POST `/api/auth/refresh`

> access_token 过期后用 refresh_token 无感续期。refresh_token 到期后需用户重新授权。

```
Request:
  Headers: Authorization: Bearer <refresh_token>

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      access_token: "eyJhb..."      // 新 access_token
    }
  }

可能的错误:
  - 1002: refresh_token 已失效 / 已过期（需重新授权登录）
```

#### GET `/api/auth/me`

```
Request:
  Headers: Authorization: Bearer <access_token>

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      id:            1,
      phone:         "138****1234",
      nickname:      "小明",
      avatar:        "https://...",
      class_name:    "计算机科学与技术 2024 级",
      dorm_building: "1 栋",
      role:          "user",
      status:        "active",
      credit_score:  100,
      created_at:    "2026-06-03T10:00:00.000Z",
      updated_at:    "2026-06-03T10:00:00.000Z"
    }
  }

可能的错误:
  - 1001: 未登录
  - 1004: 账号已被封禁
  - 1002: token 已失效
```

---

### 2.2 用户模块 `/api/users`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| GET | `/api/users/:id` | 查看用户公开信息（个人主页） | access_token |
| PUT | `/api/users/me` | 编辑个人资料 | access_token |

#### GET `/api/users/:id`

> 返回用户公开信息。`phone` 仅返回脱敏后的前 3 后 4 位，非本人不返回完整手机号。

```
Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      id:            2,
      nickname:      "小红",
      avatar:        "https://...",
      class_name:    "英语 2024 级",
      dorm_building: "3 栋",
      credit_score:  98,
      review_summary: {
        total:       5,
        avg_communication: 4.8,
        avg_punctuality:   4.6,
        avg_accuracy:      4.9
      },
      created_at: "2026-05-25T08:00:00.000Z"
    }
  }

可能的错误:
  - 2001: 用户不存在 / 已注销
```

#### PUT `/api/users/me`

> 所有字段可选，只更新传入的非空字段。

```
Request:
  Body: {
    nickname:      "小明",            // 可选，1-20 字符，敏感词过滤
    avatar:        "https://...",     // 可选，COS 图片 URL
    class_name:    "计科 2024 级",    // 可选，0-50 字符
    dorm_building: "1 栋"             // 可选，0-30 字符
  }

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      id:            1,
      nickname:      "小明",
      avatar:        "https://...",
      class_name:    "计科 2024 级",
      dorm_building: "1 栋",
      updated_at:    "2026-06-03T12:00:00.000Z"
    }
  }

可能的错误:
  - 4001: 参数校验失败（nickname 超长 / 含敏感词 → 6002）
```

---

### 2.3 商品模块 `/api/products`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| GET | `/api/products` | 商品列表（瀑布流 + 搜索 + 筛选） | access_token |
| GET | `/api/products/:id` | 商品详情（含卖家公开信息） | access_token |
| POST | `/api/products` | 发布商品 | access_token |
| PUT | `/api/products/:id` | 编辑商品 | access_token（仅本人） |
| DELETE | `/api/products/:id` | 下架/删除商品 | access_token（本人或 admin） |
| GET | `/api/products/my` | 我发布的商品列表 | access_token |

#### GET `/api/products`

```
Query 参数:
  keyword   string   搜索词（匹配商品名、分类、卖家昵称）
  category  string   分类筛选："电子产品" | "书籍教材" | "生活用品" | "服饰鞋包" | "运动户外" | "其他"
  condition string   成色筛选："全新" | "95新" | "9成新" | "8成新" | "7成新及以下"
  priceMin  number   最低价（含），单位元
  priceMax  number   最高价（含），单位元
  sort      string   排序：latest(默认) | priceAsc | priceDesc
  page      number   页码，默认 1
  pageSize  number   每页条数，默认 20，最大 50

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      list: [
        {
          id:             10,
          title:          "高等数学第七版",
          category:       "书籍教材",
          condition:      "9成新",
          price:          25.00,
          original_price: 49.80,
          cover_image:    "https://...",
          trade_location: "图书馆门前",
          negotiable:     true,
          status:         "active",
          seller: {
            id:             1,
            nickname:       "小明",
            avatar:         "https://...",
            credit_score:   100
          },
          created_at: "2026-06-03T09:00:00.000Z"
        }
      ],
      total:    45,
      page:     1,
      pageSize: 20
    }
  }

可能的错误:
  - 1001: 未登录
  - 2004: 分页参数无效（pageSize 超过最大值 50）
```

#### GET `/api/products/:id`

```
Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      id:              10,
      title:           "高等数学第七版",
      description:     "只用了一个学期，基本全新，无笔记。",
      category:        "书籍教材",
      condition:       "9成新",
      price:           25.00,
      original_price:  49.80,
      images:          ["https://...1", "https://...2"],
      trade_location:  "图书馆门前",
      negotiable:      true,
      status:          "active",
      seller: {
        id:              1,
        nickname:        "小明",
        avatar:          "https://...",
        class_name:      "计科 2024 级",
        dorm_building:   "1 栋",
        credit_score:    100,
        review_summary: {
          total:              5,
          avg_communication:  4.8,
          avg_punctuality:    4.6,
          avg_accuracy:       4.9
        }
      },
      created_at: "2026-06-03T09:00:00.000Z",
      updated_at: "2026-06-03T09:00:00.000Z"
    }
  }

可能的错误:
  - 2001: 商品不存在 / 已被删除
  - 2002: 商品已下架（仅卖家和管理员可查看）
```

#### POST `/api/products`

> 发布前检查：用户 `credit_score >= 60`，否则返回 4008。所有文本字段经敏感词过滤。

```
Request:
  Body: {
    title:          "高等数学第七版",
    description:    "只用了一个学期，基本全新",  // 可选
    category:       "书籍教材",
    condition:      "9成新",
    original_price: 49.80,
    price:          25.00,
    trade_location: "图书馆门前",
    negotiable:     true,
    images:         ["https://...1", "https://...2"]  // 1-6 张，COS 上传后返回的 URL
  }

Response (201):
  {
    code: 0,
    message: "ok",
    data: {
      id:             10,
      title:          "高等数学第七版",
      category:       "书籍教材",
      condition:      "9成新",
      price:          25.00,
      original_price: 49.80,
      images:         ["https://...1", "https://...2"],
      trade_location: "图书馆门前",
      negotiable:     true,
      status:         "active",
      created_at:     "2026-06-03T09:00:00.000Z"
    }
  }

可能的错误:
  - 4001: 必填字段缺失（title / category / condition / price / trade_location / images）
  - 4008: 信誉分不足（< 60）
  - 6002: 文本命中敏感词
```

#### PUT `/api/products/:id`

> 仅商品发布者本人可编辑。已售出 (`sold`)、已冻结 (`frozen`) 的商品不可编辑。

```
Request:
  Body: {
    title:          "高等数学第七版（已出）",  // 所有字段可选
    description:    "...",
    price:          20.00
  }

Response (200):
  {
    code: 0,
    message: "ok",
    data: { /* 更新后的完整商品信息 */ }
  }

可能的错误:
  - 2001: 商品不存在
  - 2002: 商品状态不允许编辑（sold / frozen）
  - 1005: 非本人操作（越权）
```

#### DELETE `/api/products/:id`

> 本人可软删除（`status = deleted`），管理员可强制下架（`status = off_shelf`）。

```
Request:
  Body: (可选，管理员强制下架时必填)
  {
    reason: "违规商品"     // 仅管理员下架时填写
  }

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      id:     10,
      status: "deleted",              // 或 "off_shelf"
      updated_at: "2026-06-03T12:00:00.000Z"
    }
  }

可能的错误:
  - 2001: 商品不存在
  - 1005: 非本人且非管理员
```

#### GET `/api/products/my`

```
Query 参数:
  status   string   筛选：active(默认) | reserved | sold | deleted | off_shelf | all
  page     number   页码，默认 1
  pageSize number   每页条数，默认 20

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      list: [ /* 商品对象列表 */ ],
      total:    8,
      page:     1,
      pageSize: 20
    }
  }

可能的错误:
  - 1001: 未登录
```

---

### 2.4 订单模块 `/api/orders`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| POST | `/api/orders` | 发起交易（点"我想要"） | access_token（买家） |
| GET | `/api/orders` | 我的订单列表 | access_token |
| GET | `/api/orders/:id` | 订单详情 | access_token（交易双方） |
| PUT | `/api/orders/:id/met` | 标记"已面交" | access_token（交易双方） |
| PUT | `/api/orders/:id/confirm` | 确认收货 | access_token（买家） |
| PUT | `/api/orders/:id/cancel` | 取消订单 | access_token（买家或卖家） |

#### POST `/api/orders`

> 幂等设计：`idempotent_key = ${buyer_id}_${product_id}`。重复点击"我想要"不创建新订单，返回已有订单。

```
Request:
  Body: {
    product_id: 10
  }

Response (201):
  {
    code: 0,
    message: "ok",
    data: {
      id:              100,
      product_id:      10,
      buyer_id:        2,
      seller_id:       1,
      status:          "pending",
      product_snapshot: {
        title:          "高等数学第七版",
        price:          25.00,
        trade_location: "图书馆门前",
        images:         ["https://...1"]
      },
      created_at: "2026-06-03T10:30:00.000Z"
    }
  }

可能的错误:
  - 2001: 商品不存在
  - 3003: 已存在相同订单（幂等，返回已有订单，code=0）
  - 3004: 商品已售出/已保留（status 非 active）
  - 3005: 不能购买自己发布的商品
  - 4009: 信誉分不足（< 30）
```

#### GET `/api/orders`

```
Query 参数:
  role     string   身份筛选：buyer(我买的) | seller(我卖的) | all(默认)
  status   string   状态筛选：pending | met | completed | cancelled | disputed | timeout | all(默认)
  page     number   页码，默认 1
  pageSize number   每页条数，默认 20

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      list: [
        {
          id:            100,
          product_id:    10,
          product_title: "高等数学第七版",
          cover_image:   "https://...",
          price:         25.00,
          buyer:   { id: 2, nickname: "小红", avatar: "https://..." },
          seller:  { id: 1, nickname: "小明", avatar: "https://..." },
          status:       "pending",
          met_at:       null,
          confirmed_at: null,
          created_at:   "2026-06-03T10:30:00.000Z"
        }
      ],
      total:    12,
      page:     1,
      pageSize: 20
    }
  }

可能的错误:
  - 1001: 未登录
```

#### GET `/api/orders/:id`

```
Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      id:              100,
      product_id:      10,
      buyer:   { id: 2, nickname: "小红", avatar: "https://..." },
      seller:  { id: 1, nickname: "小明", avatar: "https://..." },
      status:          "pending",
      cancelled_by:    null,
      product_snapshot: {
        title:          "高等数学第七版",
        price:          25.00,
        trade_location: "图书馆门前",
        images:         ["https://...1"]
      },
      met_at:          null,
      confirmed_at:    null,
      created_at:      "2026-06-03T10:30:00.000Z",
      updated_at:      "2026-06-03T10:30:00.000Z"
    }
  }

可能的错误:
  - 2001: 订单不存在
  - 1005: 非交易双方（越权）
```

#### PUT `/api/orders/:id/met`

> 任一方（买家或卖家）均可点击"已面交"。

```
Request:
  Body: (无)

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      id:      100,
      status:  "met",
      met_at:  "2026-06-03T14:00:00.000Z",
      updated_at: "2026-06-03T14:00:00.000Z"
    }
  }
  // 副作用: 触发 IM 系统消息通知对方"对方已确认面交"

可能的错误:
  - 2001: 订单不存在
  - 3001: 订单状态不允许此操作（仅 pending 可变为 met）
  - 1005: 非交易双方
```

#### PUT `/api/orders/:id/confirm`

> 仅买家可确认收货。确认后触发双方互评入口 + 卖家信誉分 +2。

```
Request:
  Body: (无)

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      id:           100,
      status:       "completed",
      confirmed_at: "2026-06-03T18:00:00.000Z",
      updated_at:   "2026-06-03T18:00:00.000Z"
    }
  }
  // 副作用: 商品 status → sold, 双方各获得评价入口

可能的错误:
  - 2001: 订单不存在
  - 3001: 订单状态不允许此操作（仅 met 可变为 completed）
  - 1005: 非买家本人
```

#### PUT `/api/orders/:id/cancel`

> 任一交易方可取消。取消后商品恢复 `active`。

```
Request:
  Body: (无)

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      id:           100,
      status:       "cancelled",
      cancelled_by: "buyer",          // "buyer" | "seller"
      updated_at:   "2026-06-03T13:00:00.000Z"
    }
  }
  // 副作用: 商品 status 从 reserved 恢复为 active

可能的错误:
  - 2001: 订单不存在
  - 3001: 订单状态不允许取消（仅 pending 可取消）
```

#### 订单状态流转

```
pending ──┬──→ met ──────→ completed（买家确认 / 3天自动确认）
          │
          ├──→ cancelled（任一方取消 → 商品恢复 active）
          │
          └──→ timeout（7天未面交自动取消 → 商品恢复 active）

disputed 状态（客服处理中）:
  pending/met ──举报──→ disputed ──客服裁决──→ completed / cancelled
                           │
                           └── 商品同时冻结（frozen），待裁决后恢复/下架
```

---

### 2.5 评价模块 `/api/reviews`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| POST | `/api/orders/:id/review` | 对本次交易对方提交评价 | access_token（交易双方） |
| GET | `/api/users/:id/reviews` | 查看某用户的评价列表 | access_token |

#### POST `/api/orders/:id/review`

> 每人对同一订单只能评价一次（`UNIQUE(order_id, reviewer_id, reviewee_id)`）。评价后如对方也已评价，订单关闭。
>
> **评分计算规则：** 三维度（沟通态度、守时程度、描述一致度）各 1-5 分。评价总分取三维度**平均分**（四舍五入到整数），≥ 4 星视为**好评**（被评价方信誉分 +1），≤ 2 星视为**差评**（被评价方信誉分 -5）。

```
Request:
  Body: {
    communication_score: 5,           // 沟通态度 1-5
    punctuality_score:   5,           // 守时程度 1-5
    accuracy_score:      4,           // 描述一致度 1-5
    comment:             "卖家很爽快"  // 可选，0-200 字符
  }

Response (201):
  {
    code: 0,
    message: "ok",
    data: {
      id:                  20,
      order_id:            100,
      reviewer_id:         2,         // 评价人
      reviewee_id:         1,         // 被评价人
      communication_score: 5,
      punctuality_score:   5,
      accuracy_score:      4,
      comment:             "卖家很爽快",
      created_at:          "2026-06-03T18:30:00.000Z"
    }
  }

可能的错误:
  - 2001: 订单不存在 / 订单未完成
  - 3001: 订单状态不允许评价（仅 completed 可评价）
  - 1005: 非交易双方
  - 2002: 已评价（重复提交）
```

#### GET `/api/users/:id/reviews`

```
Query 参数:
  page     number   页码，默认 1
  pageSize number   每页条数，默认 20

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      summary: {
        total:              8,
        avg_communication:  4.7,
        avg_punctuality:    4.5,
        avg_accuracy:       4.8
      },
      list: [
        {
          id:                  20,
          reviewer:  { id: 2, nickname: "小红", avatar: "https://..." },
          communication_score: 5,
          punctuality_score:   5,
          accuracy_score:      4,
          comment:             "卖家很爽快",
          created_at:          "2026-06-03T18:30:00.000Z"
        }
      ],
      total:    8,
      page:     1,
      pageSize: 20
    }
  }

可能的错误:
  - 1001: 未登录
  - 2001: 用户不存在
```

---

### 2.6 举报模块 `/api/reports`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| POST | `/api/reports` | 提交举报（用户端举报表单） | access_token |
| GET | `/api/reports` | 举报/工单列表 | access_token（cs / admin） |

#### POST `/api/reports`

> 用户提交举报后状态为 `pending`，自动创建客服工单。举报类型为"交易纠纷"时关联订单自动进入 `disputed` 状态、商品 `frozen`。
> 同一用户对同一订单不能重复提交举报（`UNIQUE(reporter_id, order_id)` 应用层校验）。

```
Request:
  Body: {
    reported_user_id: 1,
    product_id:       10,             // 可选，关联商品
    order_id:         100,            // 可选，关联订单（有则触发 disputed + frozen）
    type:             "描述不符",      // "描述不符" | "辱骂骚扰" | "疑似骗子" | "其他"
    description:      "商品实际成色与描述严重不符，有明显破损",
    evidence_images:  ["https://..."] // 可选，最多 6 张
  }

Response (201):
  {
    code: 0,
    message: "ok",
    data: {
      id:               50,
      reporter_id:      2,
      reported_user_id: 1,
      product_id:       10,
      order_id:         100,
      type:             "描述不符",
      description:      "商品实际成色与描述严重不符...",
      evidence_images:  ["https://..."],
      status:           "pending",
      created_at:       "2026-06-03T19:00:00.000Z"
    }
  }
  // 如有 order_id: 订单 status → disputed, 商品 status → frozen

可能的错误:
  - 4001: 必填字段缺失（reported_user_id / type / description）
  - 3006: 对该订单已有进行中的举报
  - 6002: 描述含敏感词
```

#### GET `/api/reports`

> 客服和管理员查看举报列表（即工单列表）。按提交时间倒序。

```
Query 参数:
  status   string   状态筛选：pending(默认) | processing | resolved | all
  type     string   类型筛选：描述不符 | 辱骂骚扰 | 疑似骗子 | 其他 | all(默认)
  page     number   页码，默认 1
  pageSize number   每页条数，默认 20

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      list: [
        {
          id:               50,
          reporter:  { id: 2, nickname: "小红", avatar: "https://..." },
          reported_user: { id: 1, nickname: "小明", avatar: "https://..." },
          product_id:       10,
          order_id:         100,
          type:             "描述不符",
          description:      "商品实际成色与描述严重不符...",
          evidence_images:  ["https://..."],
          status:           "pending",
          resolution:       null,
          resolved_at:      null,
          created_at:       "2026-06-03T19:00:00.000Z"
        }
      ],
      total:    15,
      page:     1,
      pageSize: 20
    }
  }

可能的错误:
  - 5001: 权限不足（非 cs 且非 admin）
```

---

### 2.7 客服工单模块 `/api/admin/tickets`

> 工单即举报记录（`reports` 表），客服通过此模块处理用户举报。仅 `cs` 和 `admin` 角色可访问。

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| PUT | `/api/admin/tickets/:id/process` | 标记工单为"处理中" | cs / admin |
| PUT | `/api/admin/tickets/:id/resolve` | 裁决工单（含处罚操作） | cs / admin |

#### PUT `/api/admin/tickets/:id/process`

> 客服接手处理工单。状态 `pending → processing`。

```
Request:
  Body: (无)

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      id:     50,
      status: "processing",
      updated_at: "2026-06-03T19:15:00.000Z"
    }
  }

可能的错误:
  - 2001: 工单不存在
  - 3002: 工单状态不允许（仅 pending 可标记为 processing）
  - 5001: 权限不足
```

#### PUT `/api/admin/tickets/:id/resolve`

> 客服裁决工单。需指定处理结论，可选触发处罚（信誉扣分、封号、下架商品）。裁决后工单关联的 `disputed` 订单根据 `order_action` 流转。

```
Request:
  Body: {
    resolution:     "举报属实，商品已下架，被举报用户扣 30 信誉分",
    actions: {                       // 可选，客服执行的处罚操作
      deduct_credit:  30,             // 扣减被举报人信誉分（可选）
      ban_user:       false,          // 是否封禁被举报人（可选）
      off_shelf_product: true         // 是否下架关联商品（可选）
    },
    order_action:   "cancelled"      // 可选，disputed 订单裁决结果："completed" | "cancelled"
  }

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      id:              50,
      status:          "resolved",
      resolution:      "举报属实，商品已下架，被举报用户扣 30 信誉分",
      resolved_at:     "2026-06-03T20:00:00.000Z",
      updated_at:      "2026-06-03T20:00:00.000Z",
      side_effects: {
        credit_deducted:  30,
        user_banned:      false,
        product_off_shelf: true,
        order_status:     "cancelled"
      }
    }
  }
  // 副作用: 信誉分变动 + 封禁 + 下架 + 订单恢复 + IM 系统消息通知双方

可能的错误:
  - 2001: 工单不存在
  - 3002: 工单状态不允许（仅 processing 可 resolve）
  - 5001: 权限不足
```

---

### 2.8 管理后台 — 用户管理 `/api/admin/users`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| GET | `/api/admin/users` | 用户列表 | admin |
| PUT | `/api/admin/users/:id/ban` | 封禁用户 | admin |
| PUT | `/api/admin/users/:id/unban` | 解封用户 | admin |

#### GET `/api/admin/users`

```
Query 参数:
  keyword  string   搜索词（手机号/昵称）
  status   string   状态筛选：active(默认) | banned | all
  role     string   角色筛选：user(默认) | cs | admin | all
  page     number   页码，默认 1
  pageSize number   每页条数，默认 20

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      list: [
        {
          id:            2,
          phone:         "138****5678",
          nickname:      "小红",
          avatar:        "https://...",
          class_name:    "英语 2024 级",
          dorm_building: "3 栋",
          role:          "user",
          status:        "active",
          credit_score:  98,
          created_at:    "2026-05-25T08:00:00.000Z"
        }
      ],
      total:    200,
      page:     1,
      pageSize: 20
    }
  }

可能的错误:
  - 1001: 未登录
  - 5002: 需要管理员权限
```

#### PUT `/api/admin/users/:id/ban`

> 封禁用户，即时生效：`status = banned`，`token_version += 1` 使所有已签发 token 立即失效。

```
Request:
  Body: {
    reason: "多次违规"              // 可选，写入 admin_logs
  }

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      id:     2,
      status: "banned",
      updated_at: "2026-06-03T20:00:00.000Z"
    }
  }

可能的错误:
  - 2001: 用户不存在
  - 2002: 用户已被封禁
  - 5003: 不可封禁 admin
```

#### PUT `/api/admin/users/:id/unban`

```
Request:
  Body: (无)

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      id:     2,
      status: "active",
      updated_at: "2026-06-03T21:00:00.000Z"
    }
  }

可能的错误:
  - 2001: 用户不存在
  - 2002: 用户未被封禁
```

---

### 2.9 管理后台 — 商品管理 `/api/admin/products`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| PUT | `/api/admin/products/:id/off-shelf` | 强制下架商品 | admin |

#### PUT `/api/admin/products/:id/off-shelf`

```
Request:
  Body: {
    reason: "发布违规商品"          // 必填，写入 admin_logs
  }

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      id:     10,
      status: "off_shelf",
      updated_at: "2026-06-03T20:00:00.000Z"
    }
  }
  // 副作用: 写入 admin_logs (action=off_shelf, target_type=product)

可能的错误:
  - 2001: 商品不存在
  - 2002: 商品已下架/已删除
```

---

### 2.10 管理后台 — 数据看板与统计 `/api/admin/analytics`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| GET | `/api/admin/analytics/overview` | 运营概览 | admin |
| GET | `/api/admin/analytics/categories` | 热门分类排行 | admin |
| GET | `/api/admin/analytics/search-keywords` | 热门搜索词 | admin |
| GET | `/api/admin/dashboard` | 数据看板（综合） | admin |

#### GET `/api/admin/dashboard`

> 综合数据看板，聚合展示关键指标。

```
Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      today: {
        active_users:     45,
        new_products:     12,
        new_orders:       8,
        completed_orders: 3,
        new_reports:      1
      },
      total: {
        users:        200,
        products:     350,
        orders:       180,
        dispute_rate: "4.2%"
      }
    }
  }

可能的错误:
  - 1001: 未登录
  - 5002: 需要管理员权限
```

#### GET `/api/admin/analytics/overview`

```
Query 参数:
  period  string   时间范围：today(默认) | week | month

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      dau:            [
        { date: "2026-05-28", count: 38 },
        { date: "2026-05-29", count: 42 }
      ],
      new_products:   [
        { date: "2026-05-28", count: 10 }
      ],
      new_orders:     [
        { date: "2026-05-28", count: 6 }
      ],
      new_users:      [
        { date: "2026-05-28", count: 3 }
      ]
    }
  }

可能的错误:
  - 1001: 未登录
  - 5002: 需要管理员权限
  - 2004: period 参数无效（仅支持 today/week/month）
```

#### GET `/api/admin/analytics/categories`

```
Query 参数:
  period  string   时间范围：week(默认) | month | all

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      list: [
        { category: "书籍教材", count: 120, percentage: "34.3%" },
        { category: "电子产品", count: 85,  percentage: "24.3%" }
      ]
    }
  }

可能的错误:
  - 1001: 未登录
  - 5002: 需要管理员权限
```

#### GET `/api/admin/analytics/search-keywords`

```
Query 参数:
  limit  number   返回条数，默认 20，最大 50

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      list: [
        { keyword: "高数", count: 35 },
        { keyword: "椅子", count: 28 }
      ]
    }
  }

可能的错误:
  - 1001: 未登录
  - 5002: 需要管理员权限
  - 2004: limit 参数超过最大值 50
```

---

### 2.11 上传模块 `/api/upload`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| GET | `/api/upload/cos-credential` | 获取 COS 临时上传凭证（STS） | access_token |

#### GET `/api/upload/cos-credential`

> 返回腾讯云 COS 临时密钥，前端用此凭证直传图片。密钥 30 分钟过期，限制 content-type 白名单。

```
Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      credentials: {
        tmp_secret_id:  "AKID...",
        tmp_secret_key: "xxx...",
        session_token:  "xxx..."
      },
      bucket:   "campus-market-xxx",
      region:   "ap-guangzhou",
      prefix:   "products/",
      max_size: 5242880,              // 5MB
      allowed_formats: ["jpg", "png", "webp"],
      expired_at: "2026-06-03T10:30:00.000Z"
    }
  }

可能的错误:
  - 1001: 未登录
  - 6001: COS STS 临时密钥获取失败 / COS 服务不可用
```

---

### 2.12 通知模块 `/api/notifications`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| GET | `/api/notifications` | 我的通知列表 | access_token |
| GET | `/api/notifications/unread-count` | 未读通知数量 | access_token |
| PUT | `/api/notifications/:id/read` | 标记单条已读 | access_token |
| PUT | `/api/notifications/read-all` | 全部标记已读 | access_token |

#### GET `/api/notifications`

```
Query 参数:
  type     string   类型筛选：order_update | review_remind | report_result | credit_change | all(默认)
  is_read  number   0=未读(默认) | 1=已读 | 不传=全部
  page     number   页码，默认 1
  pageSize number   每页条数，默认 20

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      list: [
        {
          id:        300,
          type:      "order_update",
          title:     "订单状态更新",
          content:   "你的订单 #100 已确认收货",
          is_read:   0,
          metadata:  { order_id: 100 },
          created_at: "2026-06-03T18:00:00.000Z"
        }
      ],
      total:    25,
      page:     1,
      pageSize: 20
    }
  }

可能的错误:
  - 1001: 未登录
  - 2004: 分页参数无效（pageSize 超过 50）
```

#### GET `/api/notifications/unread-count`

```
Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      count: 5
    }
  }

可能的错误:
  - 1001: 未登录
```

#### PUT `/api/notifications/:id/read`

```
Request:
  Body: (无)

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      id:      300,
      is_read: 1
    }
  }

可能的错误:
  - 1001: 未登录
  - 3001: 通知不存在或不属于当前用户
```

#### PUT `/api/notifications/read-all`

```
Request:
  Body: {
    type: "order_update"           // 可选，指定类型则只标记该类型；不传则全部标记
  }

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      affected: 12
    }
  }

可能的错误:
  - 1001: 未登录
```

---

### 2.13 信誉分模块 `/api/credit`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| GET | `/api/credit` | 我的信誉分 + 变动记录 | access_token |
| GET | `/api/users/:id/credit` | 查看某用户信誉分（公开） | access_token |

> **信誉分规则：**
> - 初始：**100 分**，上限：**200 分**
> - 扣分：被举报属实 **-30**；收到差评（2 星及以下）**-5**
> - 加分：完成交易卖家 **+2**；收到好评 **+1**
> - 保底：信誉分最低 **0**，不跌破
> - 限制：< 60 禁止发布商品（4008），< 30 禁止发起交易（4009）
>
> 信誉分变动记录存储在 `notifications` 表中（`type = credit_change`），不单独建表。

#### GET `/api/credit`

```
Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      score:       70,
      change_log: [
        {
          id:         301,
          type:       "credit_change",
          title:      "信誉分变动",
          content:    "你的信誉分变更为：70（-30 举报属实）",
          metadata:   { delta: -30, reason: "report_upheld", report_id: 50 },
          created_at: "2026-06-03T20:00:00.000Z"
        }
      ]
    }
  }

可能的错误:
  - 1001: 未登录
```

#### GET `/api/users/:id/credit`

```
Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      user_id: 1,
      score:   70
    }
  }
  // 公开接口，仅返回分数，不返回变动记录
```

---

### 2.14 系统健康检查

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| GET | `/api/health` | 健康检查 | — |

#### GET `/api/health`

```
Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      status:  "ok",
      uptime:  123456,
      memory:  { rss: 98, heapUsed: 45, heapTotal: 80 },
      db:      "connected",
      version: "1.0.0"
    }
  }

可能的错误:
  - 6999: 服务器内部错误
```

---

### 2.15 管理后台 — 敏感词库管理 `/api/admin/sensitive`

> 3 个管理 API 用于运维敏感词库，无需重启服务即可更新词库。

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| GET | `/api/admin/sensitive/stats` | 词库统计 | admin |
| POST | `/api/admin/sensitive/reload` | 热重载词库 | admin |
| POST | `/api/admin/sensitive/check` | 单条文本检测（调试用） | admin |

#### GET `/api/admin/sensitive/stats`

```
Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      wordCount:     550,
      loadedAt:      "2026-06-03T02:00:00.000Z",
      categories: {
        "色情低俗": 100,
        "诈骗引流": 100,
        "违规商品": 60,
        "辱骂攻击": 80,
        "政治敏感": 50,
        "广告骚扰": 50,
        "违法信息": 40,
        "赌博相关": 30,
        "校园特供": 40
      }
    }
  }

可能的错误:
  - 5002: 权限不足（非 admin）
```

#### POST `/api/admin/sensitive/reload`

```
Request:
  Body: {}

Response (200):
  {
    code: 0,
    message: "词库已重新加载",
    data: {
      wordCount:  550,
      loadedAt:   "2026-06-03T10:30:00.000Z",
      previousWordCount: 548
    }
  }

可能的错误:
  - 5002: 权限不足
  - 6999: 词库文件读取失败
```

#### POST `/api/admin/sensitive/check`

```
Request:
  Body: {
    text: "测试文本内容"              // 必填
  }

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      ok:   false,
      hits: ["裸聊", "刷单"],
      hitCount: 2
    }
  }

可能的错误:
  - 4001: text 参数缺失
  - 5002: 权限不足
```

---

### 2.16 管理后台 — 操作审计日志 `/api/admin/logs`

> 管理员敏感操作均写入 `admin_logs` 表，此接口提供事后追溯查询。

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| GET | `/api/admin/logs` | 查询操作审计日志 | admin |

#### GET `/api/admin/logs`

```
Query 参数:
  admin_id    number   可选，按操作人筛选
  target_type string   可选，按目标类型筛选：user | product | ticket
  target_id   number   可选，按目标 ID 筛选
  action      string   可选，按操作类型筛选：ban | unban | off_shelf | resolve
  start_date  string   可选，起始日期（ISO 8601）
  end_date    string   可选，截止日期（ISO 8601）
  page        number   分页，默认 1
  pageSize    number   分页，默认 20，上限 50

Response (200):
  {
    code: 0,
    message: "ok",
    data: {
      list: [
        {
          id:          42,
          admin_id:    1,
          admin_phone: "138****5678",
          action:      "off_shelf",
          target_type: "product",
          target_id:   15,
          reason:      "发布违规商品",
          created_at:  "2026-06-03T10:00:00.000Z"
        }
      ],
      total:    128,
      page:     1,
      pageSize: 20
    }
  }

可能的错误:
  - 5002: 权限不足（非 admin）
  - 4001: 参数格式错误
```

---

## 三、接口汇总

| # | 模块 | 端点 | 数量 |
|---|------|------|:--:|
| 1 | 认证 `/api/auth` | login, refresh, me | 3 |
| 2 | 用户 `/api/users` | `GET /:id`, `PUT /me` | 2 |
| 3 | 商品 `/api/products` | list, detail, create, update, delete, my | 6 |
| 4 | 订单 `/api/orders` | create, list, detail, met, confirm, cancel | 6 |
| 5 | 评价 `/api/reviews` | create (via order), list (via user) | 2 |
| 6 | 举报 `/api/reports` | create, list | 2 |
| 7 | 工单 `/api/admin/tickets` | process, resolve | 2 |
| 8 | 管理—用户 `/api/admin/users` | list, ban, unban | 3 |
| 9 | 管理—商品 `/api/admin/products` | off-shelf | 1 |
| 10 | 数据 `/api/admin/analytics` | overview, categories, search-keywords | 3 |
| 11 | 看板 `/api/admin/dashboard` | dashboard | 1 |
| 12 | 上传 `/api/upload` | cos-credential | 1 |
| 13 | 通知 `/api/notifications` | list, unread-count, read, read-all | 4 |
| 14 | 信誉分 `/api/credit` | my, user-public | 2 |
| 15 | 健康 `/api/health` | health | 1 |
| 16 | 敏感词库 `/api/admin/sensitive` | stats, reload, check | 3 |
| 17 | 审计日志 `/api/admin/logs` | list | 1 |
| **合计** | | | **43** |

---

## 四、API 错误码规范

### 4.1 错误码分段

```
1xxx — 认证与授权（未登录 / Token 无效 / 被封禁 / 越权）
2xxx — 资源（不存在 / 状态不允许）
3xxx — 业务状态冲突（订单流转 / 工单流转 / 重复操作）
4xxx — 输入与风控（参数校验 / 格式错误 / 限流 / 信誉分）
5xxx — 权限（非 cs / 非 admin / 操作系统保留用户）
6xxx — 系统与第三方（文件上传 / 敏感词 / 微信 / 腾讯云 / 未知错误）
```

### 4.2 完整错误码表

#### 1xxx — 认证与授权

| code | HTTP | message | 触发场景 |
|:----:|:----:|---------|----------|
| `1001` | 401 | 请先登录 | Authorization header 缺失或格式错误 |
| `1002` | 401 | 登录已过期，请重新登录 | Access Token 过期 / 签名无效 / 被篡改 |
| `1003` | 401 | 账号已在其他设备登录，请重新登录 | `token_version` 变更（管理员封禁触发版本号+1，所有旧 token 失效） |
| `1004` | 403 | 账号已被限制使用 | `users.status = 'banned'`，用户被封禁 |
| `1005` | 403 | 您没有权限操作该资源 | 非资源所有者尝试修改/删除他人资源 |

> **1002 vs 1003 的区别：** 1002 是 token 自身问题（过期/伪造），前端静默跳转登录页；1003 是被动失效（管理员封禁），前端应弹窗提示再跳转。

#### 2xxx — 资源

| code | HTTP | message | 触发场景 |
|:----:|:----:|---------|----------|
| `2001` | 404 | {资源类型}不存在 | 查询的用户/商品/订单/工单 ID 在数据库中不存在 |
| `2002` | 422 | {资源}状态不允许此操作 | 资源存在但当前状态不匹配操作（如已售商品不可编辑、已完成订单不可取消） |

#### 3xxx — 业务状态冲突

| code | HTTP | message | 触发场景 |
|:----:|:----:|---------|----------|
| `3001` | 409 | 订单状态不允许此操作 | 订单状态机非法转换 |
| `3002` | 409 | 工单状态不允许此操作 | 工单仅 `pending`→`processing`→`resolved`，非法跳转拒绝 |
| `3003` | 200 | （幂等返回） | 同一买家对同一商品重复下单，返回已有订单（`code=0`，不走错误码） |
| `3004` | 409 | 商品已被他人锁定 | 商品 `status` 非 `active` |
| `3005` | 409 | 不能购买自己发布的商品 | 买家 ID === 卖家 ID |
| `3006` | 409 | 已存在进行中的举报 | 同一用户对同一订单的举报尚未结案 |

#### 4xxx — 输入与风控

| code | HTTP | message | 触发场景 |
|:----:|:----:|---------|----------|
| `4001` | 400 | 请求参数不完整或格式错误 | 必填字段缺失、类型错误、枚举值不在范围内 |
| `4002` | 400 | {字段}格式不正确 | 手机号非 11 位 / 价格负数 / 评分不在 1-5 范围 |
| `4003` | 400 | 图片数量超过限制（最多 6 张） | 上传图片数组长度 > 6 |
| `4004` | 400 | 单张图片大小超过 {n}MB | 单张图片 > 5MB |
| `4005` | 400 | 不支持的图片格式 | 非 image/jpeg, image/png, image/webp |
| `4006` | 429 | 操作过于频繁，请稍后再试 | 全局限流 60 req/min 触发 |
| `4007` | 429 | 操作过于频繁，请 {n} 秒后再试 | 敏感接口限流 10 req/min（登录/发布/举报）触发 |
| `4008` | 403 | 信誉分不足，无法发布商品 | `credit_score < 60` |
| `4009` | 403 | 信誉分不足，无法参与交易 | `credit_score < 30` |

#### 5xxx — 权限

| code | HTTP | message | 触发场景 |
|:----:|:----:|---------|----------|
| `5001` | 403 | 需要客服权限 | 非 `cs` 且非 `admin` 用户访问客服工单接口 |
| `5002` | 403 | 需要管理员权限 | 非 `admin` 用户访问后台管理接口 |
| `5003` | 403 | 不可对管理员执行此操作 | 尝试封禁/操作 `role=admin` 的用户 |

#### 6xxx — 系统与第三方

| code | HTTP | message | 触发场景 |
|:----:|:----:|---------|----------|
| `6001` | 502 | 文件上传服务异常，请稍后重试 | COS STS 临时密钥获取失败 / COS 服务不可用 |
| `6002` | 400 | 内容包含违规信息，请修改后重试 | 文本命中敏感词库（不告知具体命中词） |
| `6003` | 502 | 微信服务异常，请稍后重试 | 微信 `getPhoneNumber` / `getAccessToken` 接口超时或返回错误 |
| `6004` | 502 | 消息服务异常，请稍后重试 | 腾讯云 IM REST API 调用失败 |
| `6999` | 500 | 服务器内部错误，请稍后重试 | 未预期的异常（未被 catch 的 Error / MySQL 异常 / 未知错误） |

> **6999 安全原则：** 不暴露任何堆栈信息给客户端。完整错误由 winston 记录到 error 日志，`detail` 仅在 development 环境返回。

### 4.3 错误码速查（按 HTTP 状态）

| HTTP | 涉及 code |
|:----:|-----------|
| **200** | `3003`（幂等，非错误） |
| **400** | `4001` `4002` `4003` `4004` `4005` `6002` |
| **401** | `1001` `1002` `1003` |
| **403** | `1004` `1005` `4008` `4009` `5001` `5002` `5003` |
| **404** | `2001` |
| **409** | `3001` `3002` `3004` `3005` `3006` |
| **422** | `2002` |
| **429** | `4006` `4007` |
| **500** | `6999` |
| **502** | `6001` `6003` `6004` |

---

## 五、路由 → 控制器 → 服务映射

> 以下映射表提供端点→文件的完整追溯链，解决"改一个接口应该打开哪个文件"的问题。

| # | 模块 | 端点 | 路由文件 | 控制器 | 服务层 |
|---|------|------|----------|--------|--------|
| 1 | 认证 `/api/auth` | login, refresh, me | `routes/auth.js` | `controllers/auth.js` | `services/auth.js` |
| 2 | 用户 `/api/users` | `GET /:id`, `PUT /me` | `routes/user.js` | `controllers/user.js` | `services/user.js` |
| 3 | 商品 `/api/products` | 6 端点 | `routes/product.js` | `controllers/product.js` | `services/product.js` |
| 4 | 订单 `/api/orders` | 6 端点 | `routes/order.js` | `controllers/order.js` | `services/order.js` |
| 5 | 评价 `/api/reviews` | 2 端点 | `routes/review.js` | `controllers/review.js` | `services/review.js` |
| 6 | 举报 `/api/reports` | 2 端点 | `routes/report.js` | `controllers/report.js` | `services/report.js` |
| 7 | 工单 `/api/admin/tickets` | 2 端点 | `routes/admin.js` | `controllers/admin.js` | `services/admin.js` |
| 8 | 用户管理 `/api/admin/users` | 3 端点 | `routes/admin.js` | `controllers/admin.js` | `services/admin.js` |
| 9 | 商品管理 `/api/admin/products` | off-shelf | `routes/admin.js` | `controllers/admin.js` | `services/admin.js` |
| 10 | 数据统计 `/api/admin/analytics` | 3 端点 | `routes/admin.js` | `controllers/admin.js` | `services/analytics.js` |
| 11 | 数据看板 `/api/admin/dashboard` | dashboard | `routes/admin.js` | `controllers/admin.js` | `services/analytics.js` |
| 12 | 上传 `/api/upload` | cos-credential | `routes/upload.js` | `controllers/upload.js` | `utils/cos.js`（无独立 service） |
| 13 | 通知 `/api/notifications` | 4 端点 | `routes/notification.js` | `controllers/notification.js` | `services/notification.js` |
| 14 | 信誉分 `/api/credit` | 2 端点 | `routes/credit.js` | `controllers/credit.js` | `services/credit.js` |
| 15 | 健康检查 `/api/health` | health | `app.js`（内联路由） | —（无业务逻辑） | — |
| 16 | 敏感词库 `/api/admin/sensitive` | 3 端点 | `routes/admin.js` | `controllers/admin.js` | `services/admin.js`（调用 `utils/sensitive-filter.js`） |
| 17 | 审计日志 `/api/admin/logs` | list | `routes/admin.js` | `controllers/admin.js` | `services/admin.js` |

### 中间件栈

| 中间件 | 文件 | 作用范围 |
|--------|------|---------|
| JWT 鉴权 + 封禁/信誉分检查 | `middleware/auth.js` | 除 login、health 外的所有 `/api/*` |
| 客服权限 | `middleware/cs.js` | `/api/admin/tickets` |
| 管理员权限 | `middleware/admin.js` | `/api/admin/users`、`/api/admin/products`、`/api/admin/analytics`、`/api/admin/dashboard` |
| 参数校验 | `middleware/validate.js` | POST/PUT 端点（按需挂载） |
| 限流 | `middleware/rate-limiter.js` | 全局 60 req/min；login/publish/report 独立 10 req/min |
| 错误处理 | `middleware/error-handler.js` | 全局（Express 最末中间件） |
| 访问日志 | `middleware/access-log.js` | 全局 |

### 请求调用链示例（PUT /api/orders/:id/met）

```
请求 → middleware/rate-limiter.js      → 限流检查
     → middleware/auth.js              → JWT 验证 + token_version + 封禁检查
     → routes/order.js                 → 路由匹配 PUT /:id/met
     → controllers/order.js            → orderController.markAsMet(req, res)
     → services/order.js               → orderService.markAsMet(orderId, userId)
     → repository/order.js             → orderRepo.updateStatus(orderId, 'met')
     → models/db.js                    → mysql2 pool.execute('UPDATE orders SET status=? WHERE id=?')
     → middleware/error-handler.js      → 捕获异常 → AppError → JSON 响应
```

---

## 六、前端处理指南

### 6.1 统一响应拦截器

```js
// miniprogram/api/request.js
const BASE_URL = 'https://your-api.com/api';

function request(url, options = {}) {
  const token = uni.getStorageSync('access_token');

  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      ...options,
      success(res) {
        const { statusCode, data } = res;

        // code=0 → 成功
        if (data.code === 0) {
          return resolve(data.data);
        }

        // ---- 分类处理错误 ----
        const code = data.code;

        // 1001/1002/1003: 认证失效 → 清除 token → 跳登录
        if ([1001, 1002, 1003].includes(code)) {
          uni.removeStorageSync('access_token');
          uni.removeStorageSync('refresh_token');
          uni.reLaunch({ url: '/pages/auth/login' });
          return reject(data);
        }

        // 1004: 账号被封禁 → 弹窗提示后退出
        if (code === 1004) {
          uni.showModal({
            title: '账号已被限制',
            content: data.message,
            showCancel: false,
            success: () => {
              uni.removeStorageSync('access_token');
              uni.reLaunch({ url: '/pages/auth/login' });
            },
          });
          return reject(data);
        }

        // 4001/4002: 参数错误 → Toast 提示
        if ([4001, 4002].includes(code)) {
          uni.showToast({ title: data.message, icon: 'none' });
          return reject(data);
        }

        // 4006/4007: 限流 → Toast 提示
        if ([4006, 4007].includes(code)) {
          uni.showToast({ title: data.message, icon: 'none', duration: 2500 });
          return reject(data);
        }

        // 6002: 敏感词 → 弹窗提示修改
        if (code === 6002) {
          uni.showModal({
            title: '内容审核不通过',
            content: data.message,
            showCancel: false,
          });
          return reject(data);
        }

        // 其他错误 → 通用 Toast
        uni.showToast({ title: data.message || '请求失败', icon: 'none' });
        reject(data);
      },
      fail(err) {
        uni.showToast({ title: '网络异常，请检查网络连接', icon: 'none' });
        reject(err);
      },
    });
  });
}

module.exports = {
  get: (url, data) => request(url, { method: 'GET', data }),
  post: (url, data) => request(url, { method: 'POST', data }),
  put: (url, data) => request(url, { method: 'PUT', data }),
  del: (url, data) => request(url, { method: 'DELETE', data }),
};
```

### 6.2 前端错误码判断函数

```js
export function isAuthError(code) {
  return code >= 1001 && code <= 1005;
}

export function shouldReLogin(code) {
  return [1001, 1002, 1003].includes(code);
}

export function isRateLimit(code) {
  return code === 4006 || code === 4007;
}
```

---

> **来源：** 本文档全部内容提取自 [技术架构文档 §五](技术架构文档.md)，与 DDL、IM 协议、定时任务、测试计划等其他章节保持交叉一致。API 变更时请同时更新技术架构文档和本文档。
