# 咖啡点单系统 API 接口文档

> 本文档为纯接口规范文档，描述每个接口的作用、参数、请求/响应 JSON 与错误码。
> 功能模块介绍、页面交互与业务流程请参阅 [README.md](../README.md)。

## 接口目录

> 全部接口索引，点击跳转到对应章节；模块概览见 [三、接口总览](#三接口总览)。

### 四、认证模块（16 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 4.1 | [用户注册](#41-用户注册) | `POST /api/auth/register` |
| 4.2 | [用户登录](#42-用户登录) | `POST /api/auth/login` |
| 4.3 | [发送邮箱验证码](#43-发送邮箱验证码) | `POST /api/auth/email/send-code` |
| 4.3.1 | [检查邮箱占用状态](#431-检查邮箱占用状态) | `GET /api/auth/email/check` |
| 4.4 | [邮箱验证码登录](#44-邮箱验证码登录) | `POST /api/auth/email/login` |
| 4.5 | [邮箱验证码注册](#45-邮箱验证码注册) | `POST /api/auth/email/register` |
| 4.6 | [忘记密码](#46-忘记密码申请重置令牌) | `POST /api/auth/forgot-password` |
| 4.7 | [重置密码](#47-重置密码) | `POST /api/auth/reset-password` |
| 4.8 | [查询用户信息](#48-查询用户信息) | `GET /api/auth/user/{id}` |
| 4.9 | [查询用户店铺偏好](#49-查询用户店铺偏好) | `GET /api/auth/user/{id}/preference` |
| 4.10 | [保存用户店铺偏好](#410-保存用户店铺偏好) | `PUT /api/auth/user/{id}/preference` |
| 4.11 | [更新用户个人资料](#411-更新用户个人资料) | `PUT /api/auth/user/{id}/profile` |
| 4.12 | [上传用户头像](#412-上传用户头像) | `POST /api/auth/user/{id}/avatar` |
| 4.13 | [发送绑定邮箱验证码](#413-发送绑定邮箱验证码) | `POST /api/auth/user/{id}/email/send-code` |
| 4.14 | [绑定邮箱](#414-绑定邮箱) | `PUT /api/auth/user/{id}/email` |
| 4.15 | [解绑邮箱](#415-解绑邮箱) | `DELETE /api/auth/user/{id}/email` |

### 五、游客模块（1 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 5.1 | [签发游客身份](#51-签发游客身份) | `POST /api/guest/session` |

### 六、店铺管理模块（8 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 6.1 | [创建店铺](#61-创建店铺) | `POST /api/store` |
| 6.2 | [全部店铺列表](#62-全部店铺列表) | `GET /api/store/list` |
| 6.3 | [可入驻店铺列表](#63-可入驻店铺列表) | `GET /api/store/available` |
| 6.4 | [营业中店铺列表](#64-营业中店铺列表) | `GET /api/store/open` |
| 6.5 | [店铺详情](#65-店铺详情) | `GET /api/store/{id}` |
| 6.6 | [更新店铺](#66-更新店铺) | `PUT /api/store/{id}` |
| 6.7 | [删除店铺](#67-删除店铺) | `DELETE /api/store/{id}` |
| 6.8 | [绑定商家到店铺](#68-绑定商家到店铺) | `POST /api/store/{id}/bind` |

### 七、商家模块（8 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 7.1 | [商家注册](#71-商家注册) | `POST /api/merchant/register` |
| 7.2 | [商家登录](#72-商家登录) | `POST /api/merchant/login` |
| 7.3 | [商家信息](#73-商家信息) | `GET /api/merchant/{id}` |
| 7.4 | [更新商家个人资料](#74-更新商家个人资料) | `PUT /api/merchant/{id}/profile` |
| 7.5 | [上传经营者头像](#75-上传经营者头像) | `POST /api/merchant/{id}/avatar` |
| 7.6 | [上传经营许可证](#76-上传经营许可证) | `POST /api/merchant/{id}/business-license` |
| 7.7 | [商家名下店铺](#77-商家名下店铺) | `GET /api/merchant/{id}/stores` |
| 7.8 | [经营数据看板](#78-经营数据看板) | `GET /api/merchant/{merchantId}/dashboard` |

### 八、商家菜单管理模块（6 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 8.1 | [商家菜单列表](#81-商家菜单列表) | `GET /api/store/{storeId}/menu` |
| 8.2 | [商家新增商品](#82-商家新增商品) | `POST /api/store/{storeId}/menu` |
| 8.3 | [上传商品图片](#83-上传商品图片) | `POST /api/store/{storeId}/menu/image` |
| 8.4 | [店铺可见类目](#84-店铺可见类目) | `GET /api/store/{storeId}/categories` |
| 8.5 | [商家创建自定义类目](#85-商家创建自定义类目) | `POST /api/store/{storeId}/category` |
| 8.6 | [商家更新商品](#86-商家更新商品) | `PUT /api/store/{storeId}/menu/{productId}` |

### 九、座位模块（6 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 9.1 | [分配座位](#91-分配座位) | `POST /api/seat/assign` |
| 9.2 | [解析二维码](#92-解析二维码) | `GET /api/seat/resolve` |
| 9.3 | [确认落座](#93-确认落座) | `POST /api/seat/{id}/occupy` |
| 9.4 | [离座释放](#94-离座释放) | `POST /api/seat/{id}/leave` |
| 9.5 | [座位状态列表](#95-座位状态列表) | `GET /api/seat/list` |
| 9.6 | [查询已落座座位](#96-查询已落座座位) | `GET /api/seat/occupied` |

### 十、订单模块（8 个接口 + 1 规则）

| # | 接口 | 方法与路径 |
|---|---|---|
| 10.1 | [菜单信息（用户端）](#101-菜单信息用户端) | `GET /api/menu` |
| 10.2 | [创建订单](#102-创建订单) | `POST /api/order` |
| 10.3 | [用户操作订单状态](#103-用户操作订单状态) | `POST /api/order/user/{id}/action` |
| 10.4 | [游客操作订单状态](#104-游客操作订单状态) | `POST /api/order/guest/{id}/action` |
| 10.5 | [用户订单列表](#105-用户订单列表) | `GET /api/orders/user/{userId}` |
| 10.6 | [游客订单列表](#106-游客订单列表) | `GET /api/orders/guest/{guestId}` |
| 10.7 | [全部/按店订单列表](#107-全部-按店订单列表商家端) | `GET /api/orders` |
| 10.8 | [商家操作订单状态](#108-商家操作订单状态) | `POST /api/orders/{id}/action` |
| 10.9 | [详细订单号规则](#109-详细订单号规则) | 规则说明（非接口） |

### 十一、会员中心模块（1 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 11.1 | [会员中心数据](#111-会员中心数据) | `GET /api/member/{userId}/dashboard` |

### 十二、收藏模块（4 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 12.1 | [收藏列表](#121-收藏列表) | `GET /api/favorites` |
| 12.2 | [添加收藏](#122-添加收藏) | `POST /api/favorites` |
| 12.3 | [取消收藏](#123-取消收藏) | `DELETE /api/favorites` |
| 12.4 | [游客收藏合并](#124-游客收藏合并) | `POST /api/favorites/merge` |

### 十三、会员体系模块（7 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 13.1 | [查询会员卡](#131-查询会员卡) | `GET /api/membership/card` |
| 13.2 | [开卡](#132-开卡) | `POST /api/membership/card/init` |
| 13.3 | [会员权益列表](#133-会员权益列表) | `GET /api/membership/benefits` |
| 13.4 | [等级规则](#134-等级规则) | `GET /api/membership/level-rules` |
| 13.5 | [积分兑换项](#135-积分兑换项) | `GET /api/membership/redeem-items` |
| 13.6 | [积分兑换](#136-积分兑换) | `POST /api/membership/points/redeem` |
| 13.7 | [用户卡券包](#137-用户卡券包) | `GET /api/membership/vouchers` |

### 十四、支付模块（5 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 14.1 | [创建支付单](#141-创建支付单) | `POST /api/pay/create` |
| 14.2 | [发起支付](#142-发起支付) | `POST /api/pay/pay` |
| 14.3 | [按支付单号查询](#143-按支付单号查询) | `GET /api/pay/{paymentNo}` |
| 14.4 | [按订单查询支付单](#144-按订单查询支付单) | `GET /api/pay/order/{orderId}` |
| 14.5 | [渠道回调](#145-渠道回调) | `POST /api/pay/callback/{channel}` |

### 十五、凑单模块（2 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 15.1 | [凑单进度](#151-凑单进度) | `GET /api/topup/progress` |
| 15.2 | [凑单推荐列表](#152-凑单推荐列表) | `GET /api/topup/products` |

### 十六、售后模块（4 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 16.1 | [创建售后单](#161-创建售后单) | `POST /api/after-sale` |
| 16.2 | [我的售后单列表](#162-我的售后单列表) | `GET /api/after-sale/user/{userId}` |
| 16.3 | [提交订单反馈](#163-提交订单反馈) | `POST /api/after-sale/feedback` |
| 16.4 | [我的反馈列表](#164-我的反馈列表) | `GET /api/after-sale/feedback/user/{userId}` |

### 十七、发现、秒杀与消息模块（6 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 17.1 | [商品检索](#171-商品检索) | `GET /api/discovery/search` |
| 17.2 | [为你推荐](#172-为你推荐) | `GET /api/discovery/recommendations` |
| 17.3 | [当前/最近秒杀活动](#173-当前最近秒杀活动) | `GET /api/flash-sales/current` |
| 17.4 | [我的抢购资格](#174-我的抢购资格) | `GET /api/flash-sales/claims` |
| 17.5 | [抢购秒杀资格](#175-抢购秒杀资格) | `POST /api/flash-sales/{activityId}/claim` |
| 17.6 | [用户通知](#176-用户通知) | `GET /api/notifications/user/{userId}` |

### 十八、店长增长 Agent（4 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 18.1 | [Agent 经营诊断](#181-agent-经营诊断) | `POST /api/merchant/{merchantId}/growth-agent/analyze` |
| 18.2 | [创建待确认方案](#182-创建待确认方案) | `POST /api/merchant/{merchantId}/growth-agent/actions` |
| 18.3 | [确认执行方案](#183-确认执行方案) | `POST /api/merchant/{merchantId}/growth-agent/actions/{actionId}/execute` |
| 18.4 | [查询 Agent 审计记录](#184-查询-agent-审计记录) | `GET /api/merchant/{merchantId}/growth-agent/actions` |

### 十九、顾客点单 Agent（1 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 19.1 | [生成点单方案](#191-生成点单方案) | `POST /api/customer-agent/plan` |

### 二十、外卖配送模块（17 个）

| 接口 | 方法与路径 |
|---|---|
| 顾客地址列表/新增/编辑/删除 | `GET/POST/PUT/DELETE /api/delivery/addresses`（地址按登录顾客隔离） |
| 配送员注册/登录/当前账号 | `POST /api/delivery/riders/register`、`POST /api/delivery/riders/login`、`GET /api/delivery/riders/me` |
| 配送员个人资料/头像 | `PUT /api/delivery/riders/me/profile`、`POST /api/delivery/riders/me/avatar` |
| 待抢订单 | `GET /api/delivery/rider/orders/available`（配送员令牌；仅已支付外卖订单） |
| 我的配送单 | `GET /api/delivery/rider/orders/mine`（配送员令牌） |
| 抢单 | `POST /api/delivery/rider/orders/{id}/claim`（CAS，只允许一人成功） |
| 配送状态操作 | `POST /api/delivery/rider/orders/{id}/action?action=pickup\|deliver\|complete\|release` |
| 配送员个人业绩 | `GET /api/delivery/rider/performance?range=7d\|14d\|28d\|12w`（金额、单量与趋势） |
| 配送员联系顾客 | `POST /api/delivery/rider/orders/{id}/contact/customer`（虚拟电话中介框架） |
| 顾客联系配送员 | `POST /api/delivery/orders/{orderId}/contact/rider`（虚拟电话中介框架） |
| 顾客外卖单 | `GET /api/delivery/orders/mine`（登录顾客令牌） |

### 二十一、健康检查（1 个）

| # | 接口 | 方法与路径 |
|---|---|---|
| 21.1 | [服务健康检查](#211-服务健康检查) | `GET /health` |

### 附录

| # | 内容 |
|---|---|
| A.1-A.9 | [枚举与状态表（订单状态机/支付/座位/售后/卡券/会员等级/店铺商家状态/取餐方式/商品规格）](#附录-a枚举与状态表) |

## 一、文档信息

| 项目 | 内容 |
|---|---|
| 系统名称 | 咖啡点单系统（coffee-order-system-pro） |
| 基础路径 | `http://{host}:{port}`（开发默认 `8088`；前端开发环境经 Vite 代理转发） |
| 数据格式 | JSON（UTF-8）；文件上传接口为 `multipart/form-data` |
| 版本 | v1.11.0 |

## 二、通用约定

### 2.1 响应格式

系统存在两种响应格式，各模块在章节标题处已注明：

**格式 A（`Result<T>` 包裹）**——菜单、订单、支付、凑单、售后、会员中心接口使用：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | int | 200 成功；其余为业务错误码（见 2.2） |
| message | string | 提示信息 |
| data | object/null | 业务数据，类型随接口而定 |

**格式 B（扁平业务对象）**——认证、游客、店铺、商家、座位、收藏、会员体系接口使用，直接返回 DTO 或对象；注册/登录类接口失败时用 `success=false` + `message` 字段表示；其余接口失败时由全局异常处理器返回格式 A 的错误体 `{"code":400,"message":"...","data":null}`。

### 2.2 业务错误码

> 注意：HTTP 状态码固定为 200，业务是否成功以响应体中的 `code`（格式 A）或 `success` 字段（格式 B）为准。

| code | 含义 | 典型场景 |
|---|---|---|
| 200 | 成功 | — |
| 400 | 参数错误 / 业务校验不通过 | 必填参数缺失、格式错误、积分不足、状态不允许的操作等（最常见的错误码） |
| 403 | 无权限 | 商家操作非本店订单 |
| 404 | 资源不存在 | 产品/订单/支付单/会员不存在（店铺不存在也返回 400） |
| 409 | 冲突 | 支付单重复支付、订单状态不可支付 |
| 500 | 系统异常 | 兜底错误，`message` 前缀为"系统异常: " |
| 501 | 未接入 | 真实支付渠道未接入（WECHAT/ALIPAY/BANK） |

### 2.3 鉴权说明

登录、注册及游客会话接口会返回 `accessToken`。访问需要身份校验的接口时，必须携带：

```http
Authorization: Bearer {accessToken}
```

令牌为带过期时间的 HMAC 无状态令牌；缺失、格式错误、无效或过期时返回 `401`。用户、游客和商家身份分别隔离，且路径或参数中的 `userId`、`guestId`、`merchantId` 必须与令牌身份一致，否则返回 `403`。

- `userId`：登录用户 id（`coffee_user.id`）
- `guestId`：游客标识（`g-` 前缀，由 `POST /api/guest/session` 签发）
- `merchantId`：商家 id（商家登录后取得）

### 2.4 时间格式

时间字段均为本地时间字符串，格式 `yyyy-MM-ddTHH:mm:ss`（如 `2026-08-07T14:30:00`）。

## 三、接口总览

共 89 个接口（含健康检查），按模块分组：

| # | 模块 | 接口数 | 响应格式 | 章节 |
|---|---|---|---|---|
| 1 | 认证 | 10 | B | [四](#四认证模块) |
| 2 | 游客 | 1 | B | [五](#五游客模块) |
| 3 | 店铺管理 | 8 | B | [六](#六店铺管理模块) |
| 4 | 商家 | 8 | B | [七](#七商家模块) |
| 5 | 商家菜单管理 | 6 | B | [八](#八商家菜单管理模块) |
| 6 | 座位 | 6 | B | [九](#九座位模块) |
| 7 | 订单 | 8 | A | [十](#十订单模块) |
| 8 | 会员中心 | 1 | A | [十一](#十一会员中心模块) |
| 9 | 收藏 | 4 | B | [十二](#十二收藏模块) |
| 10 | 会员体系 | 7 | B | [十三](#十三会员体系模块) |
| 11 | 支付 | 5 | A | [十四](#十四支付模块) |
| 12 | 凑单 | 2 | A | [十五](#十五凑单模块) |
| 13 | 售后 | 4 | A | [十六](#十六售后模块) |
| 14 | 发现、秒杀与消息 | 6 | B / A | [十七](#十七发现秒杀与消息模块) |
| 15 | 店长增长 Agent | 4 | A | [十八](#十八店长增长-agent) |
| 16 | 顾客点单 Agent | 1 | A | [十九](#十九顾客点单-agent) |
| 17 | 外卖配送 | 17 | A | [二十](#二十外卖配送模块) |
| 18 | 健康检查 | 1 | 纯文本 | [二十一](#二十一健康检查) |

## 四、认证模块

> 响应格式：**B**（`AuthResponse` 对象）。接口前缀 `/api/auth`。

### 4.1 用户注册

**`POST /api/auth/register`**

作用：注册新用户。用户名唯一；注册成功后返回用户信息与会员等级。

请求体（`RegisterRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | ✅ | 用户名，2-50 字符，唯一 |
| password | string | ✅ | 密码，非空 |
| nickname | string | ❌ | 昵称，缺省取用户名 |

请求示例：

```json
{
  "username": "alice",
  "password": "123456",
  "nickname": "爱丽丝"
}
```

成功响应（200）：

```json
{
  "success": true,
  "message": "操作成功",
  "id": 1,
  "accountNo": "fika4827316059",
  "username": "alice",
  "nickname": "爱丽丝",
  "totalSpent": 0.0,
  "memberLevel": "普通会员"
}
```

| 字段 | 说明 |
|---|---|
| id | 数据库内部用户 id（后续接口的身份凭证，保留自增值） |
| accountNo | 对外展示的 FIKA 账号号码；新用户为 `fika` + 10 位随机数字，历史用户由 V20 按 id 回填 |
| username | 用户设置的唯一用户名 |
| memberLevel | 普通会员 / VIP会员 / SVIP会员（按累计消费计算） |

失败响应（HTTP 200，格式 B，`success=false`）：

| message | 场景 |
|---|---|
| 用户名不能为空 | username 缺失 |
| 密码不能为空 | password 缺失 |
| 密码至少需要6个字符 / 密码必须包含至少一个字母 / 密码必须包含至少一个数字 | 密码强度校验不通过（`PasswordValidator`） |
| 用户名已存在 | 用户名被占用 |

### 4.2 用户登录

**`POST /api/auth/login`**

作用：使用用户名、系统账号号码或已绑定邮箱 + 密码登录，返回用户信息。请求字段名仍为 `username`，前端“账号”输入框会把三种登录标识统一提交到这里。

请求体（`LoginRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | ✅ | 用户名、系统账号号码（如 `fika0000000001`）或已绑定邮箱 |
| password | string | ✅ | 密码 |

请求示例：

```json
{ "username": "fika4827316059", "password": "alice2026" }
```

成功响应（200）：同 4.1（含 `totalSpent` 累计消费、`memberLevel` 当前等级）。

失败响应（HTTP 200，格式 B，`success=false`）：

| message | 场景 |
|---|---|
| 用户名或密码错误 | 参数缺失 / 用户名、账号号码或邮箱不存在 / 密码不匹配（不区分提示） |

### 4.3 发送邮箱验证码

**`POST /api/auth/email/send-code`**

作用：向指定邮箱发送一次性验证码。`LOGIN` 与 `REGISTER` 验证码相互隔离，不能跨用途使用。

请求体（`EmailCodeRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| email | string | ✅ | 接收验证码的邮箱 |
| purpose | string | ✅ | `LOGIN`（邮箱登录）或 `REGISTER`（邮箱注册） |

请求示例：

```json
{ "email": "alice@qq.com", "purpose": "LOGIN" }
```

成功响应（200）：

```json
{ "success": true, "message": "验证码已发送，请查收邮箱", "cooldownSeconds": 60 }
```

安全与频率规则：验证码默认 5 分钟有效、最多校验 5 次；验证码哈希同时写入 Redis 和 MySQL，Redis 丢失或不可用时由 MySQL 校验兜底。成功使用、过期或错误次数耗尽后都会删除 MySQL 临时记录，并同步删除 Redis key；Redis key 自带同样的 TTL。单邮箱每次发送至少间隔 60 秒；不论从登录还是注册入口申请，10 分钟窗口内成功申请第 6 次时，该邮箱冷却 10 分钟。

失败响应（格式 A 错误体）：

| code | message | 场景 |
|---|---|---|
| 400 | 请输入有效的邮箱地址 | email 格式无效 |
| 429 | 请 N 秒后再获取验证码 / 请求过于频繁 | 单次间隔或 10 分钟冷却未结束 |
| 502 | 验证码邮件发送失败，请稍后再试 | QQ SMTP 发送失败 |

### 4.3.1 检查邮箱占用状态

**`GET /api/auth/email/check?email={email}`**

作用：在邮箱注册或绑定前检查邮箱是否已被任意顾客绑定。前端会在邮箱输入框失焦时自动调用；后端注册、绑定和发送验证码时仍会再次校验，不能只依赖前端结果。

鉴权：无需登录。

成功响应（邮箱未被占用）：

```json
{
  "success": true,
  "available": true,
  "bound": false,
  "message": "该邮箱可用，可以继续注册"
}
```

邮箱已占用时：`available=false`、`bound=true`，并返回 `该邮箱已经被绑定，请选择邮箱登录`。该接口不会返回已绑定用户的身份信息。

### 4.4 邮箱验证码登录

**`POST /api/auth/email/login`**

作用：已绑定邮箱的会员使用一次性验证码登录。验证码成功后立即作废，响应会签发正常用户访问令牌。

请求体（`EmailLoginRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| email | string | ✅ | 已注册邮箱 |
| code | string | ✅ | 6 位 `LOGIN` 验证码 |

请求示例：

```json
{ "email": "alice@qq.com", "code": "123456" }
```

成功响应（200）：同 4.1（额外包含 `accessToken`）。

失败响应（HTTP 200，格式 B，`success=false`）：`验证码错误、已过期或已使用，请重新获取`；若邮箱未绑定账户则返回 `该邮箱尚未创建账户，请选择邮箱注册`。

### 4.5 邮箱验证码注册

**`POST /api/auth/email/register`**

作用：先验证邮箱，再创建一个绑定该邮箱的会员账户；成功后自动登录。

请求体（`EmailRegisterRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| email | string | ✅ | 要绑定的邮箱 |
| code | string | ✅ | 6 位 `REGISTER` 验证码 |
| username | string | ✅ | 用户设置的唯一用户名，2-50 个字符 |
| password | string | ✅ | 至少 6 位，同时含字母和数字 |
| nickname | string | ❌ | 昵称，缺省由系统设置 |

请求示例：

```json
{
  "email": "alice@qq.com",
  "code": "123456",
  "username": "alice",
  "password": "alice2026",
  "nickname": "爱丽丝"
}
```

成功响应（200）：同 4.1（额外包含 `accessToken`）。`username` 为注册时填写的唯一用户名；`accountNo` 由系统生成 `fika` + 10 位随机数字，并返回给前端，用户也可在个人资料中查看。

失败响应（HTTP 200，格式 B，`success=false`）：`该邮箱已经被绑定，请选择邮箱登录`、`用户名已存在`、`用户名长度需要在2到50个字符之间`、`验证码错误、已过期或已使用，请重新获取` 或密码强度提示。邮箱注册只允许使用尚未绑定的邮箱；邮箱注册成功后会创建用户、绑定该邮箱并生成系统账号号码。

### 4.6 忘记密码（申请重置令牌）

**`POST /api/auth/forgot-password`**

作用：为指定用户名生成密码重置令牌。生产环境统一返回模糊化成功提示，令牌必须通过站外找回渠道发送；仅在本地显式设置 `COFFEE_AUTH_EXPOSE_RESET_TOKEN=true` 时响应才包含令牌。

请求体（`ForgotPasswordRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | ✅ | 用户名 |

请求示例：

```json
{ "username": "alice" }
```

成功响应（200）：

```json
{
  "success": true,
  "message": "如果账号存在，重置流程已提交，请通过已配置的找回渠道获取令牌"
}
```

| 字段 | 说明 |
|---|---|
| token | 可选，重置令牌（30 分钟有效），提交 4.7 时使用；仅本地显式开启 `expose-reset-token` 时返回 |

失败响应（格式 A 错误体）：

| code | message | 场景 |
|---|---|---|
| 400 | 请输入用户名 | username 缺失 |

### 4.7 重置密码

**`POST /api/auth/reset-password`**

作用：用令牌 + 新密码重置密码。

请求体（`ResetPasswordRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| token | string | ✅ | 4.6 获取的重置令牌 |
| newPassword | string | ✅ | 新密码 |

请求示例：

```json
{ "token": "abc123xyz", "newPassword": "654321" }
```

成功响应（200）：同 4.1 的用户信息结构。

失败响应（HTTP 200，格式 B，`success=false`）：

| message | 场景 |
|---|---|
| 令牌不能为空 | token 缺失 |
| 新密码不能为空 | newPassword 缺失 |
| 密码至少需要6个字符 / 密码必须包含至少一个字母 / 密码必须包含至少一个数字 | 密码强度校验不通过 |
| 令牌无效或已过期 | 令牌不存在 / 已使用 / 超 30 分钟 |

### 4.8 查询用户信息

**`GET /api/auth/user/{id}`**

作用：按用户 id 查询用户信息（登录态恢复用）。

路径参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | number | ✅ | 用户 id |

成功响应（200）：同 4.1 结构。

失败响应（HTTP 200，格式 B，`success=false`）：

| message | 场景 |
|---|---|
| 用户不存在 | 用户 id 无效 |

### 4.9 查询用户店铺偏好

**`GET /api/auth/user/{id}/preference`**

作用：查询用户记住的上次选店 id（用户端启动时恢复店铺）。

路径参数：`id`（number，用户 id，必填）。

成功响应（200）：

```json
{ "success": true, "lastStoreId": 5 }
```

| 字段 | 说明 |
|---|---|
| lastStoreId | 上次选店 id；无偏好时为 `null` |

### 4.10 保存用户店铺偏好

**`PUT /api/auth/user/{id}/preference`**

作用：保存用户当前选店 id（切换店铺时调用）。

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| storeId | number | ❌ | 店铺 id；传 null 表示清除偏好 |

请求示例：

```json
{ "storeId": 5 }
```

成功响应（200）：

```json
{ "success": true, "message": "偏好已保存" }
```

### 4.11 更新用户个人资料

**`PUT /api/auth/user/{id}/profile`**

作用：保存当前登录顾客的个人资料。接口只允许修改令牌对应的用户，空字符串会被规范化为空值，因此可以清除可选资料。

请求体：

```json
{
  "nickname": "爱丽丝",
  "phone": "13800000000",
  "birthday": "1998-05-20",
  "wechatId": "alice_fika",
  "qqNumber": "123456789",
  "otherInfo": "偏好少冰"
}
```

校验规则：生日不能晚于当天；电话按格式校验；昵称 50 字符、微信号 80 字符、QQ 20 字符、其他信息 500 字符以内。邮箱不在普通资料接口中直接修改，必须使用 4.13～4.15 的邮箱绑定接口完成验证、绑定或解绑。用户最多绑定 3 个邮箱，一个邮箱只能绑定一个用户。

成功响应：`AuthResponse`，包含更新后的资料字段和新的用户访问令牌。

### 4.12 上传用户头像

**`POST /api/auth/user/{id}/avatar`**

请求格式：`multipart/form-data`，字段名 `file`。仅允许 JPG、PNG、WEBP，单张不超过 5MB。文件保存到服务端上传目录，数据库只保存 `/uploads/...` 站内相对地址。

成功响应：`AuthResponse`，其中 `avatarUrl` 为头像地址。

### 4.13 发送绑定邮箱验证码

**`POST /api/auth/user/{id}/email/send-code`**

作用：为当前登录顾客发送绑定邮箱验证码。接口会将验证码用途固定为 `BIND`，不能使用登录或注册验证码代替。

鉴权：需要当前用户的 `Bearer accessToken`，且路径中的 `id` 必须与令牌用户一致。

请求体：

```json
{ "email": "alice@qq.com" }
```

成功响应（200）：

```json
{ "success": true, "message": "绑定验证码已发送，请查收邮箱", "cooldownSeconds": 60 }
```

邮箱验证码的有效期、Redis/MySQL 双写兜底和发送频率限制与 4.3 一致。发送前会检查邮箱是否已被占用，以及当前用户是否已达到 3 个邮箱的上限。

### 4.14 绑定邮箱

**`PUT /api/auth/user/{id}/email`**

作用：校验一次绑定验证码。验证码验证成功后才会将邮箱写入当前顾客账号，并立即作废该验证码。

鉴权：需要当前用户的 `Bearer accessToken`，且路径中的 `id` 必须与令牌用户一致。

请求体：

```json
{ "email": "alice@qq.com", "code": "123456" }
```

成功响应：`AuthResponse`，包含首选邮箱 `email`、全部已绑定邮箱 `emails` 和新的用户访问令牌。第一个成功绑定的邮箱会成为首选邮箱，后续绑定不会覆盖首选邮箱。

如果该邮箱已被当前账号或其他账号绑定、当前账号已经绑定 3 个邮箱，或验证码错误、过期、已使用，接口会拒绝本次绑定。邮箱唯一性由数据库唯一索引和服务端事务共同保证。

### 4.15 解绑邮箱

**`DELETE /api/auth/user/{id}/email`**

作用：清除当前顾客账号指定的一个绑定邮箱。已登录且拥有该账号权限时，接口不再要求邮箱验证码；用户界面会先弹出确认窗口，确认后才发起请求。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| email | string | ❌ | 要解绑的邮箱；不传时兼容旧客户端，解绑当前首选邮箱 |

每次请求只解绑一个邮箱。解绑首选邮箱后，如果仍有其他邮箱，列表中的下一个邮箱会自动成为新的首选邮箱。

成功响应：`AuthResponse`，其中 `email` 为新的首选邮箱（全部解绑时为 `null`），`emails` 为剩余邮箱列表，并包含新的用户访问令牌。

## 五、游客模块

> 响应格式：**B**。接口前缀 `/api/guest`。

### 5.1 签发游客身份

**`POST /api/guest/session`**

作用：为未登录用户签发游客标识 `guestId`（`g-` 前缀），游客下单/收藏/占座均以该标识入库隔离。前端内存持有即可，无需持久化（刷新后重新签发）。

请求体：无。

成功响应（200）：

```json
{ "success": true, "guestId": "g-3f9a2c7e" }
```

## 六、店铺管理模块

> 响应格式：**B**（`StoreResponse` 对象 / 对象数组）。接口前缀 `/api/store`。

`StoreResponse` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| storeId | number | 店铺 id |
| code | string | 店铺编码（唯一） |
| name | string | 店名 |
| address | string | 地址 |
| phone | string | 联系电话 |
| businessHours | string | 营业时间（如 "09:00-21:00"） |
| status | string | `OPEN` 营业中 / `CLOSED` 已打烊 |
| merchantId | number/null | 绑定商家 id（未入驻为 null） |

### 6.1 创建店铺

**`POST /api/store`**

作用：创建店铺（管理端/商家开新店）。店铺编码唯一。

请求体（`StoreRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | string | ✅ | 店铺编码，唯一 |
| name | string | ✅ | 店名 |
| address | string | ❌ | 地址 |
| phone | string | ❌ | 电话 |
| businessHours | string | ❌ | 营业时间 |
| merchantId | number | ❌ | 绑定商家 id（创建时生效） |

请求示例：

```json
{
  "code": "SH-008",
  "name": "静安店",
  "address": "静安区南京西路100号",
  "phone": "021-88888888",
  "businessHours": "09:00-21:00"
}
```

成功响应（200）：`StoreResponse`。

失败响应：

| code | message | 场景 |
|---|---|---|
| 400 | 店铺编码不能为空 | code 缺失 |
| 400 | 店名不能为空 | name 缺失 |
| 400 | 店铺编码已存在 | code 重复 |

### 6.2 全部店铺列表

**`GET /api/store/list`**

作用：返回全部店铺（用户端选店页）。

查询参数：无。

成功响应（200）：`StoreResponse[]`。

### 6.3 可入驻店铺列表

**`GET /api/store/available`**

作用：返回可入驻店铺（种子店铺中尚未绑定商家的），商家入驻页用。

成功响应（200）：`StoreResponse[]`。

### 6.4 营业中店铺列表

**`GET /api/store/open`**

作用：返回营业中（`OPEN`）店铺，用户端左上角选店用。

成功响应（200）：`StoreResponse[]`。

### 6.5 店铺详情

**`GET /api/store/{id}`**

作用：按 id 查询店铺详情。

路径参数：`id`（number，必填）。

成功响应（200）：`StoreResponse`。

失败响应：

| code | message | 场景 |
|---|---|---|
| 400 | 店铺不存在 | id 无效 |

### 6.6 更新店铺

**`PUT /api/store/{id}`**

作用：更新店铺信息（店名/地址/电话/营业时间/营业状态）。

请求体（`StoreRequest`，`code` 不可改）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | string | ❌ | 店名 |
| address / phone / businessHours | string | ❌ | 资料字段 |
| status | string | ❌ | `OPEN` / `CLOSED`（仅更新时生效） |

请求示例：

```json
{ "name": "静安店（总店）", "businessHours": "08:00-22:00", "status": "OPEN" }
```

成功响应（200）：`StoreResponse`。

失败响应：

| code | message | 场景 |
|---|---|---|
| 400 | 店铺不存在 | id 无效 |

### 6.7 删除店铺

**`DELETE /api/store/{id}`**

作用：删除店铺。

路径参数：`id`（number，必填）。

成功响应（200）：HTTP 200，无响应体。

失败响应：

| code | message | 场景 |
|---|---|---|
| 400 | 店铺不存在 | id 无效 |

### 6.8 绑定商家到店铺

**`POST /api/store/{id}/bind?merchantId={merchantId}`**

作用：将商家绑定到店铺（商家入驻流程：激活该店预分配的占位商家记录）。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| merchantId | number | ✅ | 商家 id |

成功响应（200）：`StoreResponse`（含 `merchantId`）。

失败响应：

| code | message | 场景 |
|---|---|---|
| 400 | 店铺不存在 | id 无效 |
| 400 | 商家 id 不能为空 | merchantId 未传 |
| 400 | 商家不存在 | merchantId 无效 |
| 400 | 该店铺已被其他商家入驻 | 店铺已有商家 |

## 七、商家模块

> 响应格式：**B**。接口前缀 `/api/merchant`。

`MerchantResponse` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| success | boolean | 是否成功 |
| message | string | 提示信息 |
| id | number | 商家 id |
| merchantNo | string | 商家编号（`sj-` 前缀，商家登录账号） |
| nickname | string | 商家昵称 |
| phone | string | 联系电话 |
| avatarUrl | string | 经营者头像站内地址 |
| operatorName | string | 经营者姓名 |
| email | string | 商家邮箱 |
| businessLicenseNo | string | 经营许可证编号 |
| businessLicenseUrl | string | 经营许可证图片站内地址 |
| otherInfo | string | 商家其他资料 |
| storeName | string | 绑定店名（未入驻为 null） |
| status | string | `ACTIVE` 正常 / `DISABLED` 已禁用 |

### 7.1 商家注册

**`POST /api/merchant/register`**

作用：商家注册。支持两种模式：①传 `storeId` 入驻现有店铺（激活该店预分配占位商家并绑定）；②不传 `storeId` 开新店模式（生成独立商家档案，之后可开店/入驻）。

请求体（`MerchantRegisterRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | ✅ | 用户端账号（须已注册且未注册过商家） |
| password | string | ✅ | 须与用户端密码一致（校验后沿用同一密码） |
| nickname | string | ❌ | 商家昵称 |
| phone | string | ❌ | 联系电话 |
| storeId | number | ❌ | 入驻店铺 id；为空 = 开新店模式 |

请求示例：

```json
{
  "username": "kaoyanjuan",
  "password": "123456",
  "nickname": "老王",
  "phone": "13800138000",
  "storeId": 5
}
```

成功响应（200）：`MerchantResponse`（`success=true`，含 `merchantNo`/`id`）。

失败响应（HTTP 200，格式 B `success=false` 或格式 A 错误体）：

| 响应 | message | 场景 |
|---|---|---|
| B | 用户名不能为空 / 密码不能为空 / 密码至少 6 位 | 参数缺失/强度不足 |
| B | 用户名不存在，请先在用户端注册该账号 | 用户端账号未注册 |
| B | 该用户名已注册过商家 | 重复注册 |
| B | 密码与用户端登录密码不一致 | 密码不一致 |
| A（400） | 该店铺已被其他商家入驻 / 该店铺商家档案已激活，无法重复入驻 | storeId 对应店铺不可入驻 |
| A（400） | 该用户已绑定商家 sj-xxx，一个用户端账号只能入驻一家店 | 账号已入驻其他店 |

### 7.2 商家登录

**`POST /api/merchant/login`**

作用：商家编号 + 密码登录。

请求体（`MerchantLoginRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| merchantNo | string | ✅ | 商家编号（`sj-xxx`） |
| password | string | ✅ | 密码 |

请求示例：

```json
{ "merchantNo": "sj-687257", "password": "123456" }
```

成功响应（200）：`MerchantResponse`。

失败响应（HTTP 200，格式 B，`success=false`）：

| message | 场景 |
|---|---|
| 商家编号或密码错误 | 参数缺失 / 编号不存在 / 密码不匹配 |
| 账号已被禁用 | 状态 DISABLED |

### 7.3 商家信息

**`GET /api/merchant/{id}`**

作用：按 id 查询商家信息。

路径参数：`id`（number，必填）。

成功响应（200）：`MerchantResponse`。

### 7.4 更新商家个人资料

**`PUT /api/merchant/{id}/profile`**

作用：保存经营者昵称、姓名、联系方式、邮箱、许可证编号和其他商家资料。仅允许当前商家修改自己的资料，空字符串可以清除可选字段。

请求体示例：

```json
{
  "nickname": "FIKA 陆家嘴店",
  "operatorName": "王先生",
  "phone": "021-88888888",
  "email": "owner@example.com",
  "businessLicenseNo": "91310000XXXX",
  "otherInfo": "每日 09:00-21:00 营业"
}
```

成功响应：`MerchantResponse`。

### 7.5 上传经营者头像

**`POST /api/merchant/{id}/avatar`**

请求格式：`multipart/form-data`，字段名 `file`。仅允许 JPG、PNG、WEBP，单张不超过 5MB；成功后返回包含 `avatarUrl` 的 `MerchantResponse`。

### 7.6 上传经营许可证

**`POST /api/merchant/{id}/business-license`**

请求格式：`multipart/form-data`，字段名 `file`。仅允许 JPG、PNG、WEBP，单张不超过 5MB；成功后返回包含 `businessLicenseUrl` 的 `MerchantResponse`，商家个人资料页可直接展示。

### 7.7 商家名下店铺

**`GET /api/merchant/{id}/stores`**

作用：查询商家绑定的店铺列表（登录后"我的店铺"）。

路径参数：`id`（number，必填）。

成功响应（200）：`StoreResponse[]`。

### 7.8 经营数据看板

**`GET /api/merchant/{merchantId}/dashboard?range=7d`**

作用：商家经营看板数据——今日营业额/今日订单数/待处理订单数/营业额柱状图（可筛选范围）/最近 5 笔订单。

路径参数：`merchantId`（number，必填）。

查询参数：`range`（string，可选，默认 `7d`）——营业额柱状图范围：

| 值 | 含义 | 柱数 | 粒度 |
|---|---|---|---|
| `7d` | 近 7 天 | 7 | 按日 |
| `14d` | 半个月 | 14 | 按日 |
| `28d` | 一个月 | 28 | 按日 |
| `12w` | 一个季度 | 12 | 按周（周一起始） |

成功响应（200）：扁平 Map：

```json
{
  "store": { "storeId": 5, "code": "SH-008", "name": "静安店", "address": "静安区南京西路100号", "phone": "021-88888888", "businessHours": "09:00-21:00", "status": "OPEN", "merchantId": 12 },
  "todayRevenue": 356.5,
  "todayOrders": 28,
  "pendingOrders": 3,
  "sales": [
    { "day": "20260801", "amount": 412.0 },
    { "day": "20260802", "amount": 380.5 },
    { "day": "20260803", "amount": 0 }
  ],
  "recentOrders": [ ]
}
```

| 字段 | 说明 |
|---|---|
| store | 店铺信息（`StoreResponse`） |
| todayRevenue | 今日营业额（统计 COMPLETED/DELIVERED 订单实付金额） |
| todayOrders | 今日订单数（统计非 UNPAID 订单） |
| pendingOrders | 待商家处理订单数（PENDING/ACCEPTED/PREPARING；外卖 READY_FOR_DELIVERY 已发布配送任务，不再占用商家待处理数） |
| sales | 营业额柱状图序列 `[{day, amount}]`：`day`=YYYYMMDD（`12w` 为周起日期）；**无订单的日期/周补 0**；金额已 ROUND 2 位（统计 COMPLETED/DELIVERED） |
| recentOrders | 最近 5 笔订单（结构同 10.5，含 `orderType`） |

失败响应：

| code | message | 场景 |
|---|---|---|
| 404 | 该商家尚未入驻店铺 | 商家未绑定任何店铺 |

## 八、商家菜单管理模块

> 响应格式：**B**。接口前缀 `/api/store/{storeId}`。菜单数据按店隔离：`menu_item.store_id` 为 0 表示全局共享品（所有店可见），为 N 表示该店专属品（商家懒复制机制）。

### 8.1 商家菜单列表

**`GET /api/store/{storeId}/menu`**

作用：查询该店全部商品（含下架），商家菜单管理页用。

路径参数：`storeId`（number，必填）。

成功响应（200）：`MenuItemDTO[]`：

```json
[
  {
    "id": 1,
    "storeId": 5,
    "code": "espresso",
    "name": "意式浓缩",
    "categoryId": 1,
    "categoryCode": "coffee",
    "basePrice": 12.0,
    "priceSmall": 10.0,
    "priceMedium": 12.0,
    "priceLarge": 15.0,
    "customUnit": "ml",
    "description": "精选豆现磨",
    "imageUrl": "/uploads/5/xxx.png",
    "temperature": "HOT",
    "available": true,
    "topup": 0
  }
]
```

`MenuItemDTO` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 商品 id |
| storeId | number | 归属店铺：0=共享品 / N=该店专属品 |
| code | string | 商品编码（店内唯一） |
| name | string | 名称 |
| categoryId / categoryCode | number/string | 分类外键与编码 |
| basePrice | number | 基础价（规格价缺失兜底） |
| priceSmall / priceMedium / priceLarge | number | 三档规格定价（可空） |
| customUnit | string | 定制规格单位：coffee/tea/ice→ml，dessert/food→g |
| description | string | 描述 |
| imageUrl | string | 图片相对 URL（需 /uploads 静态代理） |
| temperature | string | HOT / BOTH / COLD / ROOM |
| available | boolean | 是否在售 |
| topup | number | 凑单标记：1=凑单推荐品 |

### 8.2 商家新增商品

**`POST /api/store/{storeId}/menu`**

作用：为指定店铺新增商品（或复制共享品为该店专属品）。商品编码店内唯一。

请求体（`MenuItemRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | string | ✅ | 商品编码（店内唯一） |
| name | string | ✅ | 名称 |
| categoryCode | string | ✅ | 分类编码（coffee/tea/dessert/food/ice 或自定义类目） |
| categoryId | number | ❌ | 分类 id（不传按 categoryCode 反查回填） |
| basePrice | number | ✅ | 基础价 |
| priceSmall / priceMedium / priceLarge | number | ❌ | 规格定价 |
| description / imageUrl / temperature | string | ❌ | 描述 / 图片 URL / 冷热 |
| topup | number | ❌ | 凑单标记（默认 0） |

请求示例：

```json
{
  "code": "espresso",
  "name": "意式浓缩",
  "categoryCode": "coffee",
  "basePrice": 12.0,
  "priceMedium": 12.0,
  "temperature": "HOT",
  "topup": 0
}
```

成功响应（200）：`MenuItemDTO`（含新商品 `id`）。

失败响应：

| code | message | 场景 |
|---|---|---|
| 400 | 商品编码不能为空 | code 缺失 |
| 400 | 该店已存在此编码的商品: xxx | code 在该店重复 |
| 404 | 产品不存在: xxx | categoryCode 反查失败 |

### 8.3 上传商品图片

**`POST /api/store/{storeId}/menu/image`**

作用：上传商品图片（`multipart/form-data`，字段名 `file`）。文件落盘 `{user.home}/coffee-uploads/{storeId}/{uuid}.{ext}`。

成功响应（200）：

```json
{ "url": "/uploads/5/7c754b2f20784adba22bd62fa6917b69.png" }
```

> `url` 为相对路径，前端拼接后端地址访问；开发环境 Vite 需代理 `/uploads`，生产环境 Nginx 需配置 `/uploads` 静态代理，否则图片 404。

失败响应：

| code | message | 场景 |
|---|---|---|
| 400 | 上传文件为空 | 未传 file 字段 |
| 500 | 系统异常: ... | 文件写入失败 |

### 8.4 店铺可见类目

**`GET /api/store/{storeId}/categories`**

作用：查询店铺可见分类 = 共享类目（store_id=0）+ 该店自定义类目。用户端菜单 tab 渲染用。

成功响应（200）：`MenuCategoryDTO[]`：

```json
[
  { "id": 1, "code": "coffee", "name": "咖啡", "icon": "☕", "storeId": 0 },
  { "id": 21, "code": "cus1234567890", "name": "手冲专区", "icon": "🏷️", "storeId": 5 }
]
```

### 8.5 商家创建自定义类目

**`POST /api/store/{storeId}/category`**

作用：商家创建自定义类目（仅本店可见）。

请求体（`MenuCategoryRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | string | ✅ | 类目名称（非空） |
| icon | string | ❌ | 图标 emoji，缺省 🏷️ |

请求示例：

```json
{ "name": "手冲专区", "icon": "☕" }
```

成功响应（200）：`MenuCategoryDTO`。服务端自动生成 `code = "cus" + 毫秒时间戳`（全局唯一）。

### 8.6 商家更新商品

**`PUT /api/store/{storeId}/menu/{productId}`**

作用：更新商品（改价/描述/上下架/凑单标记）。

路径参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| storeId | number | ✅ | 店铺 id |
| productId | number | ✅ | 商品 id |

请求体：同 8.2（`MenuItemRequest`，均可选，只更新传入字段）。

请求示例：

```json
{ "priceMedium": 13.0, "available": false, "topup": 1 }
```

成功响应（200）：`MenuItemDTO`。

失败响应：

| code | message | 场景 |
|---|---|---|
| 404 | 商品不存在: xxx | productId 无效或不属于该店 |
| 400 | 该店已存在此编码的商品: xxx | 更新 code 与店内其他商品冲突 |

## 九、座位模块

> 响应格式：**B**（`SeatResponse`）。接口前缀 `/api/seat`。

`SeatResponse` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| seatId | number | 座位 id |
| storeId | number | 店铺 id（校验座位归属） |
| storeName | string | 店名 |
| seatNo | string | 座位号（001-099） |
| code | string | 座位编号（`店名-座位号`，如 `静安店-001`） |
| capacity | int | 容纳人数 |
| status | string | `FREE` 空闲 / `ASSIGNED` 已分配 / `OCCUPIED` 已落座 |
| assignedUserId / assignedGuestId | number/string | 商家座位列表返回占用者身份；公开二维码解析接口始终为 null |
| assignedAt / occupiedAt | string | 分配时间 / 落座时间 |
| qrContent | string | 二维码内容（落座页 URL，仅分配接口返回） |
| qrBase64 | string | 二维码 PNG base64（data URL，仅分配接口返回） |

座位规则：每店预置 99 桌（001-070 双人桌、071-090 四人桌、091-099 多人桌）；`ASSIGNED` 状态 15 分钟未落座自动释放。

### 9.1 分配座位

**`POST /api/seat/assign`**

作用：按就餐人数分配空闲座位（优先匹配容量合适的），返回座位信息 + 落座二维码。分配后座位进入 `ASSIGNED`（15 分钟超时自动释放）。**一人一桌**：同一身份在本店已有未释放座位（已分配/已落座）时直接返回已有座位，不重复分配。

请求体（`AssignSeatRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| storeId | number | ✅ | 店铺 id |
| peopleCount | int | ✅ | 就餐人数（1-8） |
| userId | number | ❌ | 登录用户 id（游客不传） |
| guestId | string | ❌ | 游客标识 |

请求示例：

```json
{ "storeId": 5, "peopleCount": 2, "userId": 1 }
```

成功响应（200）：`SeatResponse`（含 `qrContent`/`qrBase64`）。

失败响应（格式 A 错误体，HTTP 200）：

| code | message | 场景 |
|---|---|---|
| 400 | 请先选择店铺 | storeId 未传 |
| 400 | 就餐人数需在 1-8 人之间 | peopleCount 越界 |
| 400 | 当前没有可用的座位，请稍后再试 | 无可匹配座位 |
| 400 | 座位分配失败，请稍后再试 | 并发下全部候选被他人抢占 |

### 9.2 解析二维码

**`GET /api/seat/resolve?code={code}`**

作用：解析二维码内容（座位编号，如 `静安店-001`），返回座位当前状态。用户扫码落座页用。

查询参数：`code`（string，必填）。

成功响应（200）：`SeatResponse`。

失败响应（格式 A 错误体，HTTP 200）：

| code | message | 场景 |
|---|---|---|
| 400 | 座位编号不能为空 | code 未传 |
| 400 | 座位编号格式不正确，应为 店名-编号 | code 格式错误 |
| 400 | 座位不存在 | code 无效 |

### 9.3 确认落座

**`POST /api/seat/{id}/occupy`**

作用：扫码后确认落座，`ASSIGNED → OCCUPIED`（占用者身份落库）。

请求体（`OccupySeatRequest`）：`userId` / `guestId`（二选一）。

请求示例：

```json
{ "userId": 1 }
```

成功响应（200）：`SeatResponse`（`status=OCCUPIED`）。

失败响应（格式 A 错误体，HTTP 200）：

| code | message | 场景 |
|---|---|---|
| 400 | 座位不存在 | id 无效 |
| 400 | 你已在「xx」取号/落座，请先离座释放再落座新座位 | 同一身份已占用其他座位 |
| 400 | 座位状态已变化，请重新扫码 | 座位非 ASSIGNED 状态 |

### 9.4 离座释放

**`POST /api/seat/{id}/leave`**

作用：离座释放座位，`OCCUPIED → FREE`（清空占用者）。

成功响应（200）：`SeatResponse`（`status=FREE`）。

失败响应（格式 A 错误体，HTTP 200）：

| code | message | 场景 |
|---|---|---|
| 400 | 座位不存在 | id 无效 |
| 400 | 该座位当前未落座 | 座位非 OCCUPIED 状态 |

### 9.5 座位状态列表

**`GET /api/seat/list?storeId={storeId}`**

作用：查询店铺座位状态列表（商家端座位管理；不传 storeId 查全部）。

查询参数：`storeId`（number，可选）。

成功响应（200）：`SeatResponse[]`。

### 9.6 查询已落座座位

**`GET /api/seat/occupied?storeId={storeId}&userId={userId}&guestId={guestId}`**

作用：按身份查询当前店已落座座位（用户端刷新后"幽灵占座"恢复）。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| storeId | number | ✅ | 店铺 id |
| userId | number | ❌ | 登录用户 id |
| guestId | string | ❌ | 游客标识（与 userId 二选一） |

成功响应（200）：`SeatResponse[]`。

## 十、订单模块

> 响应格式：**A**（`Result<T>`）。接口前缀 `/api`。
> 订单按店隔离（`user_order.store_id`）；商家只能操作本店订单。完整状态机见 [附录 A](#附录a枚举与状态表)。

### 10.1 菜单信息（用户端）

**`GET /api/menu?storeId={storeId}`**

作用：用户端菜单页数据源——商品列表 + 规格枚举 + 定制规格计价规则 + 可见类目。**不带 storeId 时商品与类目为空数组**。

查询参数：`storeId`（number，可选）。

成功响应（200）：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "products": [ { "id": 1, "storeId": 5, "code": "espresso", "name": "意式浓缩", "categoryId": 1, "categoryCode": "coffee", "basePrice": 12.0, "priceSmall": 10.0, "priceMedium": 12.0, "priceLarge": 15.0, "customUnit": "ml", "description": "精选豆现磨", "imageUrl": "/uploads/5/xxx.png", "temperature": "HOT", "available": true, "topup": 0 } ],
    "sizes": ["SMALL", "MEDIUM", "LARGE", "CUSTOM"],
    "customRule": { "baseMl": 300, "baseG": 100 },
    "categories": [ { "id": 1, "code": "coffee", "name": "咖啡", "icon": "☕", "storeId": 0 } ]
  }
}
```

| 字段 | 说明 |
|---|---|
| products | 该店可见商品（`MenuItemDTO[]`，同 8.1） |
| sizes | 可选规格枚举（CUSTOM=定制规格，按 customUnit 输入数量） |
| customRule | 定制规格计价基准：饮料类基准 300ml、餐品/甜品类基准 100g（前端展示用，实际以服务端计价为准） |
| categories | 该店可见类目（同 8.4） |

### 10.2 创建订单

**`POST /api/order`**

作用：下单。支持单品与批量（`items` 非空即批量：1 单 + N 明细，明细按行落 `order_item`）。**下单即创建支付单**（幂等编排），订单初始状态 `UNPAID`，响应携带 `paymentNo` 供前端拉起支付；支付成功后才流转 `PENDING` 进入商家队列。

请求必须带 `Idempotency-Key` Header（16-128 位字母、数字、`_` 或 `-`）。同一用户/游客身份下以相同 key 重试会原样返回首次成功响应；同 key 携带不同请求返回 `409`；同 key 正在处理返回 `409`。前端在一次“确认下单”操作中生成 UUID，网络重试时复用该 UUID。

请求体（`CreateOrderCommand`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| userId | number | ⚠️ | 登录用户 id（与 guestId 二选一） |
| guestId | string | ⚠️ | 游客标识（与 userId 二选一） |
| storeId | number | ✅ | 下单店铺 |
| fulfillmentType | string | ❌ | `PICKUP` 到店自取 / `DINE_IN` 店内用餐 / `DELIVERY` 外卖配送 |
| deliveryAddressId | number | DELIVERY 必填 | 登录顾客的收货地址 id；外卖不分配座位 |
| note | string | ❌ | 订单备注 |
| productCode | string | 单品必填 | 商品编码 |
| size | string | ❌ | `SMALL`/`MEDIUM`/`LARGE`/`CUSTOM` |
| customSize | string | ❌ | 定制尺寸输入（如 `"300"`），size=CUSTOM 时有效 |
| condiments | string[] | ❌ | 加料列表 |
| items | array | 批量必填 | 批量明细（见下） |
| couponCode | string | ❌ | 优惠券编码（FIKA8/SWEET12/BEAN15，仅会员有效） |
| flashSaleClaimNo | string | ❌ | 秒杀资格码；仅支持活动指定的单件商品，不能与优惠券叠加 |

批量明细项（`CartItemCommand`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| productCode | string | 商品编码 |
| size | string | 规格 |
| customSize | string | 定制尺寸输入 |
| condiments | string[] | 加料列表 |
| quantity | int | 数量（默认 1） |

请求示例（批量 + 会员券）：

```json
{
  "userId": 1,
  "storeId": 5,
  "fulfillmentType": "DINE_IN",
  "note": "少冰",
  "couponCode": "FIKA8",
  "items": [
    { "productCode": "espresso", "size": "MEDIUM", "quantity": 2 },
    { "productCode": "croissant", "size": "LARGE", "condiments": ["奶油"], "quantity": 1 }
  ]
}
```

成功响应（200）：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "orderId": 34,
    "orderNo": "260807-687257-001-034",
    "orderName": "意式浓缩×2、牛角包×1",
    "originalPrice": 39.0,
    "finalPrice": 31.0,
    "pricingStrategy": "下午茶立减 ¥8",
    "status": "待支付",
    "paymentNo": "PAY1723012345678",
    "message": "下单成功",
    "totalSpent": 181.0,
    "memberLevel": "VIP会员",
    "categoryCode": "coffee",
    "totalCups": 3,
    "memberDiscount": 0.0,
    "couponDiscount": 8.0,
    "couponName": "下午茶立减 ¥8",
    "earnedPoints": 0,
    "estimatedReadyTime": "14:35",
    "createdAt": "2026-08-07T14:30:00",
    "items": [
      { "productCode": "espresso", "beverageName": "意式浓缩", "categoryCode": "coffee", "size": "MEDIUM", "condiments": "", "quantity": 2, "unitPrice": 11.0, "originalUnitPrice": 13.0, "subtotal": 22.0, "estimatedReadyTime": "14:35" },
      { "productCode": "croissant", "beverageName": "牛角包", "categoryCode": "food", "size": "LARGE", "condiments": "奶油", "quantity": 1, "unitPrice": 9.0, "originalUnitPrice": 10.0, "subtotal": 9.0, "estimatedReadyTime": "14:35" }
    ]
  }
}
```

`OrderResponse` 关键字段：

| 字段 | 说明 |
|---|---|
| orderId | 订单 id（= 取餐号） |
| orderNo | 详细订单号（格式见 10.8） |
| status | 中文状态：待支付/等待商家接单/商家已接单等待制作/商家制作中/商家制作完毕待骑手接单/骑手已接单/骑手配送中/骑手已送达请取餐/已完成/已取消 |
| paymentNo | 支付单号（拉支付用）；支付单创建失败时为 null |
| deliveryOrderId | number | 外卖配送单 id；非外卖订单为 null |
| finalPrice | 实付金额（会员折扣 + 优惠券后） |
| memberDiscount / couponDiscount / couponName | 折扣明细 |
| earnedPoints | 本单预计积分（支付完成时结算） |
| estimatedReadyTime | 预计出餐时间（HH:mm） |
| items | 明细（批量订单多条；`unitPrice` 为折后单价、`originalUnitPrice` 为单件原价（折前，明细行划线展示用，老数据可能为 null）、`subtotal` 为折后小计） |

失败响应（HTTP 200，body 业务码）：

| code | message | 场景 |
|---|---|---|
| 404 | 产品不存在: xxx | productCode 无效或该店无此商品 |
| 400 | 商品数量必须大于 0 | 批量明细 quantity ≤ 0 |

> 说明：下单必须提供 `userId` 或 `guestId`，并携带与该身份匹配的 Bearer Token；`DELIVERY` 仅支持登录用户，必须传 `deliveryAddressId`，不会触发座位分配。定制规格（CUSTOM）量非法时按基准量 1.0 比例兜底计价。秒杀下单请提交单品参数（不要传 `items`），并传入未过期的 `flashSaleClaimNo`；服务端会原子核销资格，已核销或已过期的资格返回 `409`。
>
> 批量模型说明：批量订单为单条 `user_order` + 多条 `order_item` 明细；`beverageName` 拼接为「名称×数量」顿号连接，`size` 记为 `MIXED`（实际以明细为准）；会员折扣/优惠券按订单总额计算后按各商品行原价比例分摊到明细（尾差归最后一行），明细小计合计 = 实付总额。

### 10.3 用户操作订单状态

**`POST /api/order/user/{id}/action?action={action}`**

作用：登录用户操作自己的订单；当前仅允许取消待支付订单。

路径参数：`id`（number，订单 id）。查询参数：`action`（string，`cancel` 取消）。

> 状态机行为：用户端仅允许取消待支付（UNPAID）订单；已支付订单必须走售后退款流程，不能直接改为取消。非法 action 或状态不匹配会返回 400/409，订单不会被静默修改。

成功响应（200）：`Result<OrderResponse>`（`status` 为中文状态、`message` 为"状态已更新"）。

失败响应：

| code | message | 场景 |
|---|---|---|
| 404 | 订单不存在 | id 无效 |

### 10.4 游客操作订单状态

**`POST /api/order/guest/{id}/action?action={action}`**

作用：游客操作自己的订单。参数与规则同 10.3。

### 10.5 用户订单列表

**`GET /api/orders/user/{userId}`**

作用：查询该用户全部订单（按时间倒序）。待支付订单对本用户可见。

路径参数：`userId`（number，必填）。

成功响应（200）：`Result<Map[]>`，列表项字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 订单 id（取餐号） |
| orderNo | string | 详细订单号 |
| userId / guestId | number/string | 下单身份 |
| storeId | number | 店铺 id |
| fulfillmentType | string | 取餐方式 |
| note | string | 备注 |
| beverageName | string | 商品名快照 |
| size / customSize | string | 规格 / 定制尺寸 |
| condiments | string | 加料 |
| originalPrice / finalPrice | number | 原价 / 实付 |
| status | string | `UNPAID`/`PENDING`/`ACCEPTED`/`PREPARING`/`READY_FOR_DELIVERY`/`RIDER_ASSIGNED`/`DELIVERING`/`DELIVERED`/`COMPLETED`/`CANCELED` |
| createdAt | string | 下单时间 |
| estimatedReadyTime | string | 预计出餐时间 |
| items | array | 明细：`[{productId, beverageName, quantity, unitPrice, originalUnitPrice, subtotal}]`（`unitPrice` 折后单价、`originalUnitPrice` 单件原价、`subtotal` 折后小计） |

响应示例：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 34, "orderNo": "260807-687257-001-034", "userId": 1, "guestId": null,
      "storeId": 5, "fulfillmentType": "DINE_IN", "note": "少冰",
      "beverageName": "意式浓缩×2、牛角包×1", "size": "MIXED", "customSize": null,
      "condiments": "", "originalPrice": 39.0, "finalPrice": 31.0, "status": "COMPLETED",
      "createdAt": "2026-08-07T14:30:00", "estimatedReadyTime": "14:35",
      "items": [
        { "productId": 1, "beverageName": "意式浓缩", "quantity": 2, "unitPrice": 11.0, "originalUnitPrice": 13.0, "subtotal": 22.0 },
        { "productId": 3, "beverageName": "牛角包", "quantity": 1, "unitPrice": 9.0, "originalUnitPrice": 10.0, "subtotal": 9.0 }
      ]
    }
  ]
}
```

### 10.6 游客订单列表

**`GET /api/orders/guest/{guestId}`**

作用：查询该游客全部订单。结构同 10.5。

路径参数：`guestId`（string，必填）。

### 10.7 全部 / 按店订单列表（商家端）

**`GET /api/orders?storeId={storeId}&status={status}`**

作用：商家/管理端订单列表。**商家端视角不含待支付（UNPAID）订单**（列表与统计 SQL 统一过滤 `status != 'UNPAID'`）。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| storeId | number | ❌ | 按店过滤；不传返回全部 |
| status | string | ❌ | 状态过滤：`PENDING`/`ACCEPTED`/`PREPARING`/`READY_FOR_DELIVERY`/`RIDER_ASSIGNED`/`DELIVERING`/`DELIVERED`/`COMPLETED`/`CANCELED` |

成功响应（200）：`Result<Map[]>`，列表项在 10.5 基础上多 `orderType`（`user` 用户单 / `guest` 游客单）。

### 10.8 商家操作订单状态

**`POST /api/orders/{id}/action?action={action}&storeId={storeId}`**

作用：商家接单/完成/取消订单，**必须传 storeId 校验订单归属**。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| action | string | ✅ | `accept` 接单：PENDING→ACCEPTED；`start` 开始制作：ACCEPTED→PREPARING；`complete` 完成制作：非外卖 PREPARING→COMPLETED，外卖 PREPARING→READY_FOR_DELIVERY 并自动发布配送任务 |
| storeId | number | ✅ | 店铺 id（归属校验） |

成功响应（200）：`Result<OrderResponse>`（`status` 为中文描述、`message` 为"状态已更新"）。

失败响应：

| code | message | 场景 |
|---|---|---|
| 404 | 订单不存在 | id 无效 |
| 403 | 订单不属于该店铺，无权操作 | storeId 与订单店铺不匹配 |

> 状态机行为同 10.3：状态不匹配或 action 非法会返回 400/409，订单不会被静默修改；商家只能操作所属店铺订单。

### 10.9 详细订单号规则

每笔订单落库自动生成唯一订单号 `orderNo`：

```
YYMMDD-{商家6位}-{类目3位}-{顺序3位}
```

| 段位 | 长度 | 说明 | 示例 |
|---|---|---|---|
| YYMMDD | 6 | 下单日期（年-月-日） | `260807` |
| 商家段 | 6 | 店铺绑定商家的 `merchant_no` 去 `sj-` 前缀；无商家回退店铺 id 补 6 位 | `687257` |
| 类目段 | 3 | 首行商品类目 id（`menu_category.id`）左补 0；无类目为 `000` | `001` |
| 顺序段 | 3 | 店铺当日单号（当日第 N 单，跨分类连续，超 999 进位 4 位） | `034` |

要点：`user_order.order_no` 唯一索引 `uk_order_no`，并发同号时服务端自动重算重试（最多 3 次）；批量订单只生成 1 个订单号，取餐号 = 订单 `id`。

## 十一、会员中心模块

> 响应格式：**A**。接口前缀 `/api/member`。本模块为只读展示数据源（旧会员中心）；开卡/兑换/卡券包等操作能力见十三、会员体系模块。

### 11.1 会员中心数据

**`GET /api/member/{userId}/dashboard`**

作用：用户端"会员中心"页数据源。用户不存在时按零消费处理，不报错。

路径参数：`userId`（number，必填）。

成功响应（200）：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "nickname": "Alice",
    "totalSpent": 181.0,
    "totalSaved": 23.5,
    "memberLevel": "VIP会员",
    "points": 181,
    "pointsLevel": "BRONZE",
    "nextThreshold": 500,
    "amountToNext": 319.0,
    "progress": 36,
    "coupons": [
      { "code": "FIKA8", "name": "下午茶立减 ¥8", "minimum": 48, "discount": 8, "description": "下午茶品类满 ¥48 立减 ¥8" },
      { "code": "SWEET12", "name": "甜品满 ¥78 减 ¥12", "minimum": 78, "discount": 12, "description": "甜品品类满 ¥78 立减 ¥12" },
      { "code": "BEAN15", "name": "咖啡满 ¥88 减 ¥15", "minimum": 88, "discount": 15, "description": "咖啡品类满 ¥88 立减 ¥15" }
    ]
  }
}
```

| 字段 | 说明 |
|---|---|
| nickname | 昵称（未注册/不存在时为"会员"） |
| totalSpent | 累计消费 |
| totalSaved | 累计优惠金额（原价-实付，计入 COMPLETED/DELIVERED 订单） |
| memberLevel | 普通会员（<100）/ VIP会员（≥100）/ SVIP会员（≥500） |
| points | 积分 |
| pointsLevel | 积分等级（BRONZE 等，预留） |
| nextThreshold | 距下一等级门槛（SVIP 封顶） |
| amountToNext | 距下一等级差额 |
| progress | 升级进度百分比（0-100） |
| coupons | 固定三张权益券（会员下单可用） |

## 十二、收藏模块

> 响应格式：**B**。接口前缀 `/api/favorites`。收藏按身份隔离入库（`user_favorite` 表：登录用户按 userId、游客按 guestId），添加/取消幂等。

### 12.1 收藏列表

**`GET /api/favorites?userId={userId}`** 或 **`GET /api/favorites?guestId={guestId}`**

作用：查询该身份的全部收藏商品（按 id 倒序）。

查询参数：`userId`（number）与 `guestId`（string）二选一，必填其一。

成功响应（200）：`MenuItemDTO[]`（同 8.1，含 `storeId`/`available`/`topup`）。

### 12.2 添加收藏

**`POST /api/favorites`**

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| userId | number | ⚠️ | 登录用户 id（与 guestId 二选一） |
| guestId | string | ⚠️ | 游客标识 |
| productCode | string | ✅ | 商品编码 |

请求示例：

```json
{ "userId": 1, "productCode": "latte" }
```

成功响应（200）：

```json
{ "success": true, "message": "已添加到收藏" }
```

> 幂等说明：重复收藏（同身份同商品）静默返回成功；userId/guestId 必须且只能传一个，缺失或同时传入会返回 400；商品编码不能为空，商品是否存在由菜单数据约束和前端传值共同保证。

### 12.3 取消收藏

**`DELETE /api/favorites?userId={userId}&productCode={code}`**（或 `guestId={guestId}`）

查询参数：`userId`/`guestId`（二选一）、`productCode`（必填）。

成功响应（200）：

```json
{ "success": true, "message": "已取消收藏" }
```

### 12.4 游客收藏合并

**`POST /api/favorites/merge`**

作用：游客登录后调用，把该游客标识下的收藏迁移到用户账号（商品并集去重）。

请求体：`{ "userId": 1, "guestId": "g-xxx" }`。

成功响应（200）：

```json
{ "success": true, "message": "收藏已合并" }
```

## 十三、会员体系模块

> 响应格式：**B**（直接返回业务对象）；失败时由全局异常处理器返回格式 A 错误体。接口前缀 `/api/membership`。
> 与十一（只读 dashboard）并存：本模块提供开卡、兑换、卡券包等操作能力。积分账本为 `member_card`，卡券包为 `user_voucher`。

### 13.1 查询会员卡

**`GET /api/membership/card?userId={userId}`**

作用：查询用户会员卡。未开卡返回 `null`。

查询参数：`userId`（number，必填）。

成功响应（200）：

```json
{
  "id": 1, "userId": 1, "cardNo": "CARD-8f3a2c", "level": "VIP",
  "points": 181, "totalSpent": 181.0, "exchangePoints": 0,
  "status": 1, "discountRate": 0.95,
  "createdAt": "2026-08-06T10:00:00", "updatedAt": "2026-08-06T10:00:00"
}
```

| 字段 | 说明 |
|---|---|
| level | `REGULAR` / `VIP` / `SVIP` |
| status | 1 正常 / 0 冻结 |
| discountRate | 等级折扣率（REGULAR 1.0 / VIP 0.95 / SVIP 0.9） |
| exchangePoints | 累计已兑换积分 |

### 13.2 开卡

**`POST /api/membership/card/init`**

作用：开卡（幂等，已开卡直接返回现有卡）。从 `coffee_user` 消费记录快照初始化等级与积分。

请求体：`{ "userId": 1 }`。

成功响应（200）：`MemberCardDTO`（同 13.1）。

失败响应：

| code | message | 场景 |
|---|---|---|
| 400 | 无效用户 | userId 缺失/非法 |
| 404 | 会员不存在 | 用户未注册 |

### 13.3 会员权益列表

**`GET /api/membership/benefits?userId={userId}`**

作用：按当前等级计算的可享权益（等级折扣 + 权益券），会员中心展示用。

成功响应（200）：`BenefitDTO[]`：

```json
[
  { "code": "LEVEL_DISCOUNT", "name": "等级折扣", "type": "DISCOUNT", "discountRate": 0.95, "minimum": null, "discount": null, "description": "VIP会员 95 折" },
  { "code": "FIKA8", "name": "下午茶立减 ¥8", "type": "COUPON", "discountRate": null, "minimum": 48, "discount": 8, "description": "下午茶品类满 ¥48 立减 ¥8" }
]
```

### 13.4 等级规则

**`GET /api/membership/level-rules`**

作用：返回等级档位与积分规则（前端免硬编码）。

成功响应（200）：

```json
{
  "levels": [
    { "level": "REGULAR", "label": "普通会员", "threshold": 0, "discountRate": 1.0 },
    { "level": "VIP", "label": "VIP会员", "threshold": 100, "discountRate": 0.95 },
    { "level": "SVIP", "label": "SVIP会员", "threshold": 500, "discountRate": 0.9 }
  ],
  "pointsRule": "每消费 1 元积 1 分（按实付金额与等级倍率计算）"
}
```

### 13.5 积分兑换项

**`GET /api/membership/redeem-items`**

作用：可兑换项列表（接口下发，前端免硬编码）。

成功响应（200）：`RedeemItemDTO[]`：

```json
[
  {
    "code": "R500",
    "name": "12 元无门槛券 ×5 + 10 元无门槛券 ×4",
    "costPoints": 1000,
    "grants": [
      { "name": "12 元无门槛券", "discount": 12, "count": 5 },
      { "name": "10 元无门槛券", "discount": 10, "count": 4 }
    ]
  }
]
```

### 13.6 积分兑换

**`POST /api/membership/points/redeem`**

作用：兑换积分项，校验积分充足后扣减积分，按 `grants` 拆券发放到卡券包（无门槛券）。

请求体：`{ "userId": 1, "itemCode": "R500" }`。

成功响应（200）：`RedeemResultDTO`：

```json
{
  "itemCode": "R500",
  "itemName": "12 元无门槛券 ×5 + 10 元无门槛券 ×4",
  "costPoints": 1000,
  "remainingPoints": 500,
  "vouchers": [
    { "id": 10, "voucherNo": "VCH8a3f", "name": "12 元无门槛券", "discount": 12, "minimum": 0, "status": 0, "source": "REDEEM", "createdAt": "2026-08-07T15:00:00", "expiresAt": "2026-09-06T15:00:00" }
  ],
  "message": "兑换成功"
}
```

失败响应：

| code | message | 场景 |
|---|---|---|
| 400 | 无效用户 | userId 缺失/非法 |
| 400 | 兑换项不存在: xxx | itemCode 无效 |
| 400 | 会员卡已冻结，无法兑换 | 卡状态非正常 |
| 400 | 积分不足，需要 xxx 分，当前 xxx 分 | 余额不足 |

### 13.7 用户卡券包

**`GET /api/membership/vouchers?userId={userId}`**

作用：该用户持有的全部卡券。

成功响应（200）：`VoucherDTO[]`：

```json
[
  { "id": 10, "voucherNo": "VCH8a3f", "name": "12 元无门槛券", "discount": 12, "minimum": 0, "status": 0, "source": "REDEEM", "createdAt": "2026-08-07T15:00:00", "expiresAt": "2026-09-06T15:00:00" }
]
```

| 字段 | 说明 |
|---|---|
| discount | 面额 |
| minimum | 使用门槛（0=无门槛） |
| status | 0 未使用 / 1 已使用 / 2 已过期 |
| source | `REDEEM` 积分兑换 / `GIFT` 赠送 |

## 十四、支付模块

> 响应格式：**A**。接口前缀 `/api/pay`。
> 渠道策略：`MOCK`（模拟支付，直接成功）+ `WECHAT`/`ALIPAY`/`BANK`（骨架占位，调用返回 501 未接入）。支付状态：`PENDING` 待支付 / `PROCESSING` 处理中 / `PAID` 已支付 / `FAILED` 支付失败 / `CLOSED` 已关闭 / `REFUNDED` 已退款。

`PaymentResponse` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| paymentId | number | 支付单 id |
| paymentNo | string | 支付单号（`PAY` + 时间戳 + 4 位随机，唯一） |
| orderId | number | 关联订单 id |
| userId | number/null | 下单用户 id（游客单为 null） |
| channel | string | 渠道 WECHAT/ALIPAY/BANK/MOCK |
| amount | number | 支付金额 |
| status | string | PENDING/PROCESSING/PAID/FAILED/CLOSED/REFUNDED |
| statusDesc | string | 状态中文描述 |
| transactionNo | string | 渠道流水号（支付成功后生成） |
| paidAt / createdAt | string | 支付时间 / 创建时间 |

### 14.1 创建支付单

**`POST /api/pay/create`**

作用：为订单创建支付单（幂等：同订单已有支付单直接返回）。下单接口内部已自动创建，本接口用于补建场景（如下单时支付单创建失败）。

请求体：`{ "orderId": 34 }`。

成功响应（200）：`Result<PaymentResponse>`。

失败响应：

| code | message | 场景 |
|---|---|---|
| 404 | 订单不存在 | orderId 无效 |

### 14.2 发起支付

**`POST /api/pay/pay`**

作用：发起支付。渠道 `MOCK` 直接成功（订单随即流转 PENDING）；真实渠道骨架返回 501。

请求体：`{ "paymentNo": "PAYxxx", "channel": "MOCK" }`。

成功响应（200）：`Result<PaymentResponse>`（`status=PAID`、`statusDesc="已支付"`、含 `transactionNo`）。

失败响应：

| code | message | 场景 |
|---|---|---|
| 404 | 支付单不存在 | paymentNo 无效 |
| 409 | 支付单已支付或已关闭，请勿重复支付 | 重复支付/已关闭 |
| 409 | 订单当前状态不可支付，请刷新后重试 | 订单已取消等不可支付状态（`checkPayable` 校验） |
| 501 | 该渠道暂未接入 | WECHAT/ALIPAY/BANK 骨架未接入 |
| 400 | 不支持的支付渠道: xxx | channel 非 WECHAT/ALIPAY/BANK/MOCK |

### 14.3 按支付单号查询

**`GET /api/pay/{paymentNo}`**

作用：按支付单号查询支付单状态（前端支付结果轮询/回显）。

成功响应（200）：`Result<PaymentResponse>`。

### 14.4 按订单查询支付单

**`GET /api/pay/order/{orderId}`**

作用：按订单查询支付单（用户端"去支付"入口用）。

成功响应（200）：`Result<PaymentResponse>`。

失败响应：

| code | message | 场景 |
|---|---|---|
| 404 | 该订单暂无支付单 | 该订单未创建支付单 |

### 14.5 渠道回调

**`POST /api/pay/callback/{channel}`**

作用：渠道异步回调统一入口（当前仅显式启用的 MOCK 回调可用；真实渠道接入前不会开放）。回调必须携带 `X-Payment-Callback-Secret`，并校验支付单号、金额和渠道；幂等可重放，重复回调不重复入账。

路径参数：`channel`（string，`WECHAT`/`ALIPAY`/`BANK`/`MOCK`）。

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| paymentNo | string | ✅ | 支付单号 |
| transactionNo | string | ❌ | 渠道流水号 |

请求示例：

```json
{ "paymentNo": "PAYxxx", "transactionNo": "WX20260807150000001" }
```

成功响应（200）：`Result<PaymentResponse>`。

失败响应：

| code | message | 场景 |
|---|---|---|
| 404 | 支付单不存在 | paymentNo 无效 |
| 501 | 该渠道暂未接入 | 真实渠道未接入（MOCK 渠道回调可用） |
| 400 | 不支持的支付渠道: xxx | channel 非法 |

## 十五、凑单模块

> 响应格式：**A**。接口前缀 `/api/topup`。
> 业务规则：候选满减券 = 卡券包未使用且带门槛（minimum>0）的券 + 固定三张权益券（FIKA8 满48减8 / SWEET12 满78减12 / BEAN15 满88减15），游客只看固定券；取"门槛 > 购物袋金额"的最小门槛为凑单目标；凑单推荐品 = 标记 `topup=1` 且最低可买价 ≤ 还差金额的商品（按最低价升序）。

### 15.1 凑单进度

**`GET /api/topup/progress?userId={userId}&amount={amount}&couponCode={couponCode}`**

作用：购物袋满减进度条数据——距最近一个未达成满减门槛还差多少。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| userId | number | ❌ | 登录用户 id（游客不传，只看固定权益券） |
| amount | number | ❌ | 购物袋金额（会员折后、券前） |
| couponCode | string | ❌ | 已选优惠券编码（可空） |

成功响应（200）：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "reached": false,
    "threshold": 48.0,
    "gap": 8.0,
    "couponName": "下午茶立减 ¥8",
    "couponCode": "FIKA8",
    "discount": 8.0
  }
}
```

| 字段 | 说明 |
|---|---|
| reached | 是否已满足全部门槛（true 时无需凑单，gap=0，threshold=最高门槛） |
| threshold | 目标门槛金额 |
| gap | 还差金额 = threshold - amount |
| couponName / couponCode | 目标券名 / 编码（卡券包券无固定编码，couponCode 为空串） |
| discount | 目标券面额 |

无候选券时：`{"reached": true, "threshold": 0, "gap": 0, "couponName": "", "couponCode": "", "discount": 0}`。

### 15.2 凑单推荐列表

**`GET /api/topup/products?storeId={storeId}&maxPrice={maxPrice}`**

作用：该店可用凑单品（`topup=1` 且在售），仅推最低可买价 ≤ 还差金额的，按最低价升序。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| storeId | number | ✅ | 店铺 id |
| maxPrice | number | ❌ | 最高可买价（传 15.1 的 gap；不传默认 0=只推免费品） |

成功响应（200）：`Result<MenuItemDTO[]>`（同 8.1 结构；`topup=1`）。

## 十六、售后模块

> 响应格式：**A**。接口前缀 `/api/after-sale`。
> 规则：仅**已完成（COMPLETED）或骑手已送达（DELIVERED）**订单可售后/反馈；订单必须属于该用户（游客不能售后）；同一订单防重复提交。

### 16.1 创建售后单

**`POST /api/after-sale`**

请求体（`AfterSaleRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| userId | number | ✅ | 售后用户 id |
| orderId | number | ✅ | 关联订单 id（取餐号） |
| type | string | ✅ | `REFUND` 退款 / `REMAKE` 重做 / `EXCHANGE` 换货 / `OTHER` 其他 |
| reason | string | ✅ | 问题说明（至少 4 字，最长 500） |

请求示例：

```json
{ "userId": 1, "orderId": 34, "type": "REFUND", "reason": "咖啡洒了，申请退款" }
```

成功响应（200）：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1, "orderId": 34, "orderNo": "260807-687257-001-034", "orderName": "意式浓缩×2",
    "type": "REFUND", "reason": "咖啡洒了，申请退款", "status": "PENDING",
    "handlerNote": null, "createdAt": "2026-08-07T16:00:00", "updatedAt": "2026-08-07T16:00:00"
  }
}
```

| 字段 | 说明 |
|---|---|
| status | PENDING 待处理 / PROCESSING 处理中 / RESOLVED 已解决 / REJECTED 已拒绝 / CLOSED 已关闭（新单为 PENDING） |
| handlerNote | 商家处理备注（预留） |

失败响应：

| code | message | 场景 |
|---|---|---|
| 400 | 请先登录后再申请售后 | userId 缺失 |
| 400 | 缺少订单信息 | orderId 缺失 |
| 400 | 请选择售后类型 | type 不合法 |
| 400 | 请描述问题（至少 4 个字） | reason 太短 |
| 404 | 订单不存在 | 订单不存在或非本人 |
| 400 | 仅已完成或骑手已送达订单可以申请售后 | 订单状态非 COMPLETED/DELIVERED |
| 400 | 该订单已提交过售后申请，请耐心等待处理 | 防重复提交 |

### 16.2 我的售后单列表

**`GET /api/after-sale/user/{userId}`**

作用：该用户全部售后单（按创建时间倒序）。售后弹窗"我的售后记录"用。

成功响应（200）：`Result<AfterSaleResponse[]>`。

### 16.3 提交订单反馈

**`POST /api/after-sale/feedback`**

请求体（`FeedbackRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| userId | number | ✅ | 反馈用户 id |
| orderId | number | ✅ | 关联订单 id |
| content | string | ✅ | 建议内容（至少 2 字） |
| rating | number | ❌ | 评分 1-5 |

请求示例：

```json
{ "userId": 1, "orderId": 34, "content": "希望多出一些低糖选项", "rating": 4 }
```

成功响应（200）：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1, "orderId": 34, "orderNo": "260807-687257-001-034", "orderName": "意式浓缩×2",
    "content": "希望多出一些低糖选项", "rating": 4, "createdAt": "2026-08-07T16:10:00"
  }
}
```

失败响应：

| code | message | 场景 |
|---|---|---|
| 400 | 请先登录后再提交反馈 | userId 缺失 |
| 400 | 缺少订单信息 | orderId 缺失 |
| 400 | 建议内容不能为空 | content 缺失 |
| 400 | 评分范围 1-5 | rating 越界 |
| 404 | 订单不存在 | 订单不存在或非本人 |
| 400 | 仅已完成或骑手已送达订单可以提交反馈 | 订单状态非 COMPLETED/DELIVERED |

### 16.4 我的反馈列表

**`GET /api/after-sale/feedback/user/{userId}`**

作用：该用户全部反馈（按创建时间倒序）。

成功响应（200）：`Result<FeedbackResponse[]>`。

## 十七、发现、秒杀与消息模块

### 17.1 商品检索

**`GET /api/discovery/search`**

作用：按商品名称、描述或类目在指定店铺内检索商品。无需登录。

查询参数：`storeId`（必填）、`keyword`（必填）、`limit`（可选，默认 12，范围 1-30）。

成功响应（200）：`MenuItemDTO[]`（扁平数组）。关键词为空时返回空数组。

### 17.2 为你推荐

**`GET /api/discovery/recommendations`**

作用：基于用户或游客的收藏类目推荐当前店铺商品；未收藏时默认优先推荐咖啡类。

查询参数：`storeId`（必填）、`userId` / `guestId`（二选一）、`limit`（可选，默认 8，范围 1-12）。携带身份参数时必须同时携带匹配身份的 Bearer Token。

成功响应（200）：`MenuItemDTO[]`（扁平数组）。

### 17.3 当前/最近秒杀活动

**`GET /api/flash-sales/current`**

作用：查询店铺已经开始的最近三场秒杀活动。售罄或结束的活动不会从列表中消失，而是保留并以灰态展示，提示用户下次提前准备。

查询参数：`storeId`（必填）。无需登录。

成功响应（200，扁平数组）：

```json
[
  {
    "id": 1,
    "productCode": "latte",
    "title": "经典拿铁 · 限时尝鲜",
    "flashPrice": 16.9,
    "availableStock": 29,
    "startAt": "2026-08-09T17:00:00",
    "endAt": "2026-08-09T17:30:00",
    "claimable": true,
    "closeReason": null
  }
]
```

`closeReason` 为 `SOLD_OUT` 表示已售罄，为 `ENDED` 表示活动结束；此时 `claimable=false`。

### 17.4 我的抢购资格

**`GET /api/flash-sales/claims`**

作用：查询用户或游客的抢购记录（最多 50 条）。

查询参数：`userId` / `guestId` 二选一，并携带对应身份的 Bearer Token。

成功响应（200，扁平数组）：

```json
[
  {
    "claimNo": "FS9DD110E72E6444A8",
    "status": "CLAIMED",
    "claimedAt": "2026-08-09T17:17:00",
    "expiresAt": "2026-08-09T17:27:00",
    "productCode": "latte",
    "title": "经典拿铁 · 限时尝鲜",
    "flashPrice": 16.9
  }
]
```

状态说明：`CLAIMED` 待下单核销、`USED` 已核销、`EXPIRED` 已过期。资格自抢到起保留 10 分钟；过期记录前端应灰显并对抢购码加删除线。

### 17.5 抢购秒杀资格

**`POST /api/flash-sales/{activityId}/claim`**

作用：抢占一份秒杀资格。每个用户/游客对同一活动限购一份；返回的 `claimNo` 应保存并用于创建秒杀订单。

请求头：`Authorization: Bearer {accessToken}`。

请求体：

```json
{ "userId": 1 }
```

或游客：

```json
{ "guestId": "g-xxx" }
```

成功响应（200，扁平对象）：

```json
{
  "id": 1,
  "productCode": "latte",
  "title": "经典拿铁 · 限时尝鲜",
  "flashPrice": 16.9,
  "availableStock": 30,
  "claimNo": "FS9DD110E72E6444A8",
  "message": "抢购成功，抢购资格已保存"
}
```

失败时：`409` 表示已售罄或重复抢购；`404` 表示活动不存在或已结束。

> 抢购成功后，登录用户会收到一条 `FLASH_SALE_CLAIM` 通知。资格在 10 分钟内未下单核销时，30 秒轮询会以条件更新将它置为 `EXPIRED` 并归还库存；核销和过期并发时仅允许其中一次状态流转成功。

### 17.6 用户通知

**`GET /api/notifications/user/{userId}`**

作用：获取用户最近 50 条站内消息（包括秒杀抢购成功提醒）。

请求头：`Authorization: Bearer {accessToken}`，路径中的 `userId` 必须与令牌身份一致。

成功响应（200，格式 A）：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "type": "FLASH_SALE_CLAIM",
      "title": "抢购成功 · 资格已保存",
      "content": "你已抢到「经典拿铁 · 限时尝鲜」，抢购码：FS9DD110E72E6444A8。可在会员中心的“我的抢购”中查看。",
      "readStatus": 0,
      "createdAt": "2026-08-09T17:17:00"
    }
  ]
}
```

## 十八、店长增长 Agent

> 响应格式：**A**。所有接口需要商家 Bearer Token，且 `merchantId` 必须与 Token 身份一致。执行营销动作前必须先创建待确认方案，再调用执行接口，不能由 Agent 直接写入业务数据。

### 18.1 Agent 经营诊断

**`POST /api/merchant/{merchantId}/growth-agent/analyze`**

作用：读取今日订单、已完成营业额、履约队列和秒杀库存，返回诊断证据以及一个可控的营销建议；该接口只读，不会发券或触达用户。

请求体：

```json
{ "message": "为什么今天营业额不高？给我一个不打扰顾客的增长方案。" }
```

成功响应的 `data` 包含：

| 字段 | 说明 |
|---|---|
| answer | Agent 对本次工具调用的说明 |
| signals | 经营信号数组（订单、营业额、履约、秒杀库存） |
| snapshot | 原始统计快照：`todayOrders`、`todayRevenue`、`pendingOrders`、`weekRevenue`、`flashSaleStock` |
| suggestedAction | 待确认动作：`actionType`、`title`、`summary`、`reason`、`proposal` |
| toolCalls | 本次诊断实际调用的只读工具、状态、耗时和返回条数 |
| executionPlan | Agent 展示给店长的受控执行步骤 |
| requiresConfirmation | 是否需要店长确认后才能进入写操作链路 |
| engine | 当前执行引擎标识：`FIKA Growth Agent · rule-tools` |

`actionType` 当前只允许 `NOTIFY_MEMBERS`（站内通知）和 `CREATE_VOUCHERS`（发券并通知）。规则引擎可替换为 LLM Function Calling，但 LLM 只能选择受控工具，不能获得直接写库权限。

### 18.2 创建待确认方案

**`POST /api/merchant/{merchantId}/growth-agent/actions`**

作用：将 Agent 建议写入 `growth_agent_action`，状态为 `PENDING`。必须由商家进一步确认后才执行。

请求体：

```json
{
  "actionType": "CREATE_VOUCHERS",
  "title": "老客唤醒 · 满48减8限时券",
  "proposal": {
    "discount": 8,
    "minimum": 48,
    "targetDays": 30,
    "expiresDays": 3,
    "message": "FIKA 为你留了一张满 ¥48 减 ¥8 的限时心意券，3 天内可用。"
  }
}
```

### 18.3 确认执行方案

**`POST /api/merchant/{merchantId}/growth-agent/actions/{actionId}/execute`**

作用：以状态条件更新锁定 `PENDING` 方案并执行。`NOTIFY_MEMBERS` 向近期开单用户写入站内通知；`CREATE_VOUCHERS` 发放卡券并写入通知。重复执行返回 `409`。

成功响应：`data.affectedUsers` 为本次实际触达的用户数，执行状态会写入审计表。

### 18.4 查询 Agent 审计记录

**`GET /api/merchant/{merchantId}/growth-agent/actions?limit=20`**

作用：查询最近的 Agent 方案与执行记录。`limit` 范围 1-50，默认 20。

成功响应：`data` 为数组，包含 `id`、`actionType`、`title`、`status`、`createdAt`、`executedAt`。

> 使用前先执行 [V20260831_12_runtime_consistency.sql](../sql/migrations/V20260831_12_runtime_consistency.sql) 创建审计表及相关运行时表。

### 18.5 统一只读 Agent 与运行轨迹

**`POST /api/business-agent/ask`**

作用：根据 `scene` 在顾客或商家范围内生成结构化计划，并执行当前场景允许的只读工具。模型输出会经过工具白名单、场景和只读属性校验；该接口不会直接下单、扣款、发券或修改库存。

请求体：

```json
{
  "scene": "merchant",
  "storeId": 5,
  "message": "最近复购下降，但今天待制作订单很多，应该怎么做？"
}
```

成功响应的 `data` 包含 `runId`、`structuredPlan`、`tools`、`answer` 和 `engine`。`structuredPlan.steps` 中的每一步包含 `tool`、受限 `arguments` 和 `purpose`；`tools` 中包含 `callId`、`latencyMs`、`readOnly`、`success` 和返回数据。

**`POST /api/business-agent/stream`**

以 SSE 推送 `status`、`plan`、`tools`、`delta` 和 `done` 事件；`plan` 与 `done` 事件均携带 `runId`。

**`GET /api/business-agent/runs/{runId}`**

仅返回当前身份自己的 Agent 运行轨迹。执行 [V20260906_17_agent_observability.sql](../sql/migrations/V20260906_17_agent_observability.sql) 后，响应会包含运行状态和按顺序排列的工具调用记录；未执行迁移时只提示审计不可用，不影响只读 Agent 降级回答。

## 十九、顾客点单 Agent

> 响应格式：**A**。该接口只输出当前菜单中的受控商品方案，绝不直接创建订单；顾客确认后仍由前端调用 `POST /api/order`，因此身份校验、订单幂等、库存、服务端计价和支付流程保持不变。

### 19.1 生成点单方案

**`POST /api/customer-agent/plan`**

作用：将顾客的自然语言需求转换为 1-2 个可直接下单的商品搭配。推荐会综合当前店铺菜单、个人收藏、近期开单热度、单品反馈评分和冷热/品类/预算关键词；没有偏好或反馈数据时自动降级为菜单需求匹配与随机探索。

请求头：`Authorization: Bearer {accessToken}`。

请求体：

```json
{
  "storeId": 5,
  "userId": 1,
  "message": "下午有点困，想喝清爽一点、别太苦的，顺便配个小甜点。"
}
```

游客将 `userId` 替换为 `guestId`，并携带游客 Token。

成功响应的 `data` 示例：

```json
{
  "reply": "我推荐「经典拿铁」，再搭配「芝士蛋糕」。它与你这次描述的口味最接近；确认后我会直接带你去支付。",
  "items": [
    {
      "productCode": "latte",
      "name": "经典拿铁",
      "temperature": "BOTH",
      "size": "MEDIUM",
      "quantity": 1,
      "estimatedPrice": 24.0,
      "reason": "近期销量表现突出"
    }
  ],
  "signals": [
    { "label": "你的偏好", "value": "已参考 2 个收藏", "used": true },
    { "label": "门店销量", "value": "本周热销", "used": true }
  ],
  "note": "确认后会直接创建待支付订单并进入收银台；最终价格、库存和优惠以服务端结算为准。"
}
```

前端确认方案时应使用返回的 `planToken` 调用下方确认接口；不要把 Agent 返回的估算金额当作最终应付金额，也不要在浏览器重新提交商品行。

### 19.2 确认 Agent 方案并创建待支付订单

**`POST /api/customer-agent/plans/confirm`**

作用：顾客确认后，直接由服务端消费 `planToken` 中绑定的方案快照并创建 `UNPAID` 订单，响应携带 `paymentNo`，前端应立即打开付款窗口。浏览器**不得**再次提交商品、价格或数量。

请求头：`Authorization: Bearer {accessToken}`、`Idempotency-Key: {16-128 位随机键}`。

请求体：

```json
{
  "planToken": "Agent 返回的一次性令牌",
  "storeId": 5,
  "userId": 1,
  "fulfillmentType": "PICKUP"
}
```

安全规则：令牌绑定用户/游客身份与门店，5 分钟过期，只能绑定一枚幂等键；换身份、换门店、修改商品行或用另一枚幂等键重复确认均会被拒绝。价格、库存、优惠与支付单均由正式订单链路处理。

## 二十、外卖配送模块

> 模块实现：`coffee-module-delivery-biz`。顾客地址接口使用顾客 Bearer Token；配送员接口使用 `RIDER` Bearer Token。外卖配送必须是登录顾客订单，暂不支持游客外卖和配送费计算。

### 20.1 顾客地址列表

**`GET /api/delivery/addresses`**

返回当前登录顾客的地址列表，默认地址排在前面。

### 20.2 新增/编辑/删除顾客地址

**`POST /api/delivery/addresses`**、**`PUT /api/delivery/addresses/{id}`**、**`DELETE /api/delivery/addresses/{id}`**

新增和编辑请求体：

```json
{
  "label": "公司",
  "receiverName": "小林",
  "receiverPhone": "13800000000",
  "detailAddress": "静安区某某路 88 号 12 楼",
  "isDefault": true
}
```

服务端按当前登录用户校验地址归属；每个账号最多保存 20 条。地址被配送单使用时会复制成快照，后续编辑地址不影响已下单配送单。

### 20.3 创建外卖订单

**`POST /api/order`**

在原订单请求体上使用 `fulfillmentType: "DELIVERY"` 和 `deliveryAddressId`。示例：

```json
{
  "userId": 1,
  "guestId": null,
  "storeId": 5,
  "fulfillmentType": "DELIVERY",
  "deliveryAddressId": 12,
  "items": [{ "productCode": "LATTE", "size": "MEDIUM", "quantity": 1, "condiments": [] }]
}
```

订单创建时会自动生成一条 `WAITING_MERCHANT` 配送单；支付成功后主订单进入 `PENDING`（等待商家接单），商家依次接单、开始制作、完成制作，主订单进入 `READY_FOR_DELIVERY` 后配送单才会发布为 `OPEN`，出现在配送员待抢列表。此流程不会分配座位。

### 20.4 配送员注册/登录/当前账号

**`POST /api/delivery/riders/register`**、**`POST /api/delivery/riders/login`**、**`GET /api/delivery/riders/me`**

注册请求体：`{ "username": "rider01", "password": "abc123", "nickname": "小林", "phone": "13800000000" }`。登录/注册成功响应中的 `accessToken` 只能用于配送员接口。

### 20.5 待抢订单与我的配送单

**`GET /api/delivery/rider/orders/available`**、**`GET /api/delivery/rider/orders/mine`**

待抢订单只返回 `OPEN` 且对应主订单为 `READY_FOR_DELIVERY` 的配送单；响应包含门店、商品摘要、收货地址快照、金额和配送状态。

骑手响应会隐藏顾客真实手机号和用户内部 id；骑手仅可看到收货人称呼、地址标签、详细地址、商品摘要和顾客备注。顾客手机号只用于服务端地址快照和后续中介转接，不会透传给骑手。

### 20.6 抢单与状态操作

**`POST /api/delivery/rider/orders/{id}/claim`**

抢单使用数据库条件更新，同一配送单只有一个请求能成功；被抢走时返回 `409`。

**`POST /api/delivery/rider/orders/{id}/action?action={action}`**

| action | 前置状态 | 下一个状态 |
|---|---|---|
| `pickup` | `CLAIMED` | `PICKED_UP` |
| `deliver` | `PICKED_UP` | `DELIVERING` |
| `complete` | `DELIVERING` | `DELIVERED` |
| `release` | `CLAIMED` | `OPEN` |

### 20.7 配送员个人业绩

**`GET /api/delivery/rider/performance`**

返回当前登录配送员自己的业绩数据，不接受外部 rider id。通过 `range` 查询参数切换看板范围：`7d` 近 7 天、`14d` 近 14 天、`28d` 近 1 个月、`12w` 近 1 个季度；不传时默认 `7d`。`7d/14d/28d` 按自然日返回趋势，`12w` 按周返回趋势。

金额口径为该配送员已送达配送单的订单金额，不等同于骑手收入；配送费规则接入后再替换为实际结算金额。

```json
{
  "range": "7d",
  "rangeLabel": "近 7 天",
  "bucket": "DAY",
  "rangeAssigned": 3,
  "rangeDelivered": 2,
  "rangeAmount": 52.80,
  "averageOrderAmount": 26.40,
  "todayAssigned": 3,
  "todayDelivered": 2,
  "activeOrders": 1,
  "weekDelivered": 12,
  "totalDelivered": 86,
  "totalDeliveredAmount": 4820.60,
  "deliveryFeeConfigured": false,
  "deliveryFeeLabel": "配送费规则待接入",
  "daily": [
    { "day": "20260826", "delivered": 0, "amount": 0.00 },
    { "day": "20260901", "delivered": 2, "amount": 52.80 }
  ]
}
```

`daily` 会补齐所选范围内的所有日/周，即使当天没有完成配送也会返回 `0`，前端可以直接绘制连续趋势图。

### 20.8 虚拟电话联系框架

**`POST /api/delivery/rider/orders/{id}/contact/customer`**
**`POST /api/delivery/orders/{orderId}/contact/rider`**

两端均通过自身身份发起联系：骑手只能联系自己已接单的配送单，顾客只能联系自己的外卖主订单，且订单必须处于已接单/已取餐/配送中的可联系阶段。当前默认实现只生成 5 分钟有效的一次性 `relayId`，返回 `NOT_CONFIGURED`，不返回真实手机号，也不发起真实通话；后续接入第三方虚拟号码中介时替换应用层适配器即可。

### 20.9 顾客外卖配送单

**`GET /api/delivery/orders/mine`**

登录顾客查询自己的配送单及状态，地址字段来自下单时的快照。

### 20.10 配送员个人资料

**`PUT /api/delivery/riders/me/profile`**

保存当前登录配送员的昵称、电话、生日、邮箱和其他资料；只能修改自己的账号，生日不能晚于当天，空字符串可清除可选字段。

**`POST /api/delivery/riders/me/avatar`**

请求格式：`multipart/form-data`，字段名 `file`。仅允许 JPG、PNG、WEBP，单张不超过 5MB。接口只返回站内相对地址，不会把骑手真实资料暴露给顾客。

## 二十一、健康检查

### 21.1 服务健康检查

**`GET /health`**

作用：服务存活探针（负载均衡/部署脚本用）。

成功响应（200）：纯文本 `OK`。

## 附录 A：枚举与状态表

### A.1 订单状态机

| 状态（DB） | 中文 | 流转 |
|---|---|---|
| UNPAID | 待支付 | 下单初始态；[支付成功]→PENDING；可取消→CANCELED |
| PENDING | 等待商家接单 | 商家 accept→ACCEPTED |
| ACCEPTED | 商家已接单，等待制作 | 商家 start→PREPARING |
| PREPARING | 商家制作中 | 商家 complete→COMPLETED（非外卖）或 READY_FOR_DELIVERY（外卖） |
| READY_FOR_DELIVERY | 商家制作完毕，待骑手接单 | 骑手 claim→RIDER_ASSIGNED |
| RIDER_ASSIGNED | 骑手已接单 | 骑手 deliver→DELIVERING（取餐动作不改变主订单状态） |
| DELIVERING | 骑手配送中 | 骑手 complete→DELIVERED |
| DELIVERED | 骑手已送达，请取餐 | 终态；平台写入取餐通知 |
| COMPLETED | 已完成 | 非外卖终态 |
| CANCELED | 已取消 | 终态 |

> 商家端列表/统计统一过滤 `UNPAID`；外卖完整状态机：`UNPAID →[支付成功]→ PENDING →[accept]→ ACCEPTED →[start]→ PREPARING →[complete]→ READY_FOR_DELIVERY →[claim]→ RIDER_ASSIGNED →[deliver]→ DELIVERING →[complete]→ DELIVERED`。到店自取/店内用餐在 `complete` 后进入 `COMPLETED`。

### A.2 支付状态

| 状态 | 说明 |
|---|---|
| PENDING | 待支付 |
| PAID | 已支付（订单流转 PENDING） |
| FAILED | 支付失败 |
| CLOSED | 已关闭（超时未付等） |
| REFUNDED | 已退款 |

渠道：`MOCK` 模拟（直接成功）/ `WECHAT` / `ALIPAY` / `BANK`（后三者未接入，返回 501）。

### A.3 座位状态

| 状态 | 说明 | 流转 |
|---|---|---|
| FREE | 空闲 | 分配→ASSIGNED |
| ASSIGNED | 已分配（等待落座） | 落座→OCCUPIED；15 分钟超时自动释放→FREE |
| OCCUPIED | 已落座 | 离座 leave→FREE |

### A.4 售后状态

| 状态 | 说明 |
|---|---|
| PENDING | 待处理 |
| PROCESSING | 处理中 |
| RESOLVED | 已解决 |
| REJECTED | 已拒绝 |
| CLOSED | 已关闭 |

售后类型：`REFUND` 退款 / `REMAKE` 重做 / `EXCHANGE` 换货 / `OTHER` 其他。

### A.5 卡券状态

| status | 说明 |
|---|---|
| 0 | 未使用 |
| 1 | 已使用 |
| 2 | 已过期 |

来源：`REDEEM` 积分兑换 / `GIFT` 赠送。

### A.6 会员等级

| 等级 | 门槛（累计消费） | 折扣 | 标签 |
|---|---|---|---|
| REGULAR | 0 | 1.0（无折扣） | 普通会员 |
| VIP | ≥ 100 | 0.95 | VIP会员 |
| SVIP | ≥ 500 | 0.90 | SVIP会员 |

固定权益券：`FIKA8` 满 48 减 8 / `SWEET12` 满 78 减 12 / `BEAN15` 满 88 减 15（仅会员下单可用）。

### A.7 店铺 / 商家状态

| 枚举 | 值 | 说明 |
|---|---|---|
| StoreStatus | OPEN / CLOSED | 营业中 / 已打烊 |
| MerchantStatus | ACTIVE / DISABLED | 正常 / 已禁用 |

### A.8 订单取餐方式

`PICKUP` 到店自取 / `DINE_IN` 店内用餐 / `DELIVERY` 外卖配送（登录顾客 + 收货地址，不分配座位）。

### A.9 商品规格

`SMALL` / `MEDIUM` / `LARGE` / `CUSTOM`（定制规格：按 `customUnit` 输入数量，价 = 中等款 × 量 ÷ 基准，饮料类基准 300ml、餐品/甜品类基准 100g）。
# 定位与门店推荐（coffee-module-location-biz）

- `POST /api/location/user`：登录用户上报最近位置，body 为 `{ "userId": 1, "latitude": 31.23, "longitude": 121.46 }`。服务端限制中国大陆经纬度范围，并按用户幂等更新。
- `GET /api/location/recommend?latitude=31.23&longitude=121.46&limit=5`：返回营业门店，按球面距离升序；响应包含 `distanceKm`、门店坐标和基础门店信息。

首次部署请执行 `sql/migrations/V20260831_12_runtime_consistency.sql`（已包含 `user_location` 表），再根据实际门店地址校正 `LocationApplicationService` 中的坐标种子。`sql_backup/location_module.sql` 仅保留作历史单模块部署参考。
