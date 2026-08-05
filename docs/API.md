# FIKA 咖啡点单系统 — 接口文档

> 本文档为系统唯一接口权威文档。新增接口时必须同步补充至对应模块章节，并更新"接口总览"。

## 文档信息

| 项目 | 内容 |
|---|---|
| 文档版本 | v1.1.0 |
| 最后更新 | 2026-08-05 |
| 适用后端 | coffee-order-system-pro_back（Spring Boot 3.2.0） |
| 维护规则 | 新增/修改接口后，在本文档对应模块补充章节，并递增文档版本号 |

---

## 一、通用约定

### 1.1 Base URL

| 环境 | 地址 |
|---|---|
| 本地开发 | `http://localhost:8088` |
| 前端代理 | Vite 开发服务器 `http://localhost:5173`，`/api` 前缀自动转发至后端 |

所有接口路径均以 `/api` 开头。

### 1.2 请求规范

- Content-Type：`application/json; charset=utf-8`（POST/PUT 请求体）
- 字符编码：UTF-8

### 1.3 响应格式

系统存在**两种**响应格式，按模块区分：

**格式 A：统一响应 `Result<T>`**（订单 / 菜单 / 会员中心 / 收藏等模块）

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { }
}
```

**格式 B：直接响应 `AuthResponse`**（认证模块 `/api/auth/*`）

```json
{
  "success": true,
  "message": "操作成功",
  "id": 1,
  "username": "alice",
  "nickname": "Alice",
  "totalSpent": 0.0,
  "memberLevel": "普通会员"
}
```

> 前端 axios 拦截器已做统一解包：格式 A 自动取 `data` 字段，格式 B 原样透传。前端调用方无需关心两种格式差异。

### 1.4 业务错误码

| code | 含义 | 说明 |
|---|---|---|
| 200 | 成功 | 操作成功 |
| 400 | 参数错误 | 请求参数缺失 / 格式非法（如密码强度不足） |
| 404 | 资源不存在 | 如产品不存在、订单不存在 |
| 500 | 系统异常 | 未捕获异常，由 GlobalExceptionHandler 兜底 |

### 1.5 安全规范

- 密码存储：BCrypt 哈希（工作因子 12），**绝不存明文**
- 密码强度：至少 6 位，必须同时包含字母和数字
- 旧用户兼容：历史明文密码首次登录成功时自动升级为 BCrypt 哈希
- 密码重置：一次性令牌（SecureRandom 32 字节），30 分钟有效，使用后立即作废；重新申请时旧令牌全部失效
- 防用户枚举：忘记密码接口对不存在用户返回统一文案，不暴露账号是否存在

---

## 二、接口总览

| 模块 | 接口 | 方法 | 说明 | 文档状态 |
|---|---|---|---|---|
| Auth | `/api/auth/register` | POST | 用户注册 | ✅ 已编写 |
| Auth | `/api/auth/login` | POST | 用户登录 | ✅ 已编写 |
| Auth | `/api/auth/forgot-password` | POST | 忘记密码（获取重置令牌） | ✅ 已编写 |
| Auth | `/api/auth/reset-password` | POST | 重置密码 | ✅ 已编写 |
| Auth | `/api/auth/user/{id}` | GET | 获取用户信息 | ✅ 已编写 |
| 菜单 | `/api/menu` | GET | 获取菜单（产品列表+规格） | ⏳ 待补充 |
| 订单 | `/api/order` | POST | 创建订单 | ⏳ 待补充 |
| 订单 | `/api/orders/user/{userId}` | GET | 用户订单列表 | ⏳ 待补充 |
| 订单 | `/api/orders/guest/{guestId}` | GET | 游客订单列表 | ⏳ 待补充 |
| 订单 | `/api/orders` | GET | 全部订单 | ⏳ 待补充 |
| 订单 | `/api/order/user/{id}/action` | POST | 更新用户订单状态 | ⏳ 待补充 |
| 订单 | `/api/order/guest/{id}/action` | POST | 更新游客订单状态 | ⏳ 待补充 |
| 会员 | `/api/member/{userId}/dashboard` | GET | 会员中心数据 | ⏳ 待补充 |
| 收藏 | `/api/favorites/{userId}` | GET | 收藏列表 | ⏳ 待补充 |
| 收藏 | `/api/favorites` | POST | 添加收藏 | ⏳ 待补充 |
| 收藏 | `/api/favorites/{userId}/{productCode}` | DELETE | 取消收藏 | ⏳ 待补充 |
| 座位 | `/api/seat/assign` | POST | 按人数分配座位（返回落座二维码） | ✅ 已编写 |
| 座位 | `/api/seat/resolve` | GET | 解析二维码内容（座位编号） | ✅ 已编写 |
| 座位 | `/api/seat/{id}/occupy` | POST | 确认落座 | ✅ 已编写 |
| 座位 | `/api/seat/{id}/leave` | POST | 离座释放 | ✅ 已编写 |
| 座位 | `/api/seat/list` | GET | 全部座位状态（管理用） | ✅ 已编写 |
| 商家 | `/api/merchant/register` | POST | 商家注册（自动生成 sj-编号） | ✅ 已编写 |
| 商家 | `/api/merchant/login` | POST | 商家登录（商家编号+密码） | ✅ 已编写 |
| 商家 | `/api/merchant/{id}` | GET | 商家信息 | ✅ 已编写 |
| 商家 | `/api/merchant/{id}/stores` | GET | 商家名下店铺（入驻状态） | ✅ 已编写 |
| 店铺 | `/api/store` | POST | 创建店铺（开新店） | ✅ 已编写 |
| 店铺 | `/api/store/list` | GET | 全部店铺列表 | ✅ 已编写 |
| 店铺 | `/api/store/available` | GET | 可入驻店铺列表 | ✅ 已编写 |
| 店铺 | `/api/store/{id}` | GET | 店铺详情 | ✅ 已编写 |
| 店铺 | `/api/store/{id}` | PUT | 更新店铺 | ✅ 已编写 |
| 店铺 | `/api/store/{id}` | DELETE | 删除店铺 | ✅ 已编写 |
| 店铺 | `/api/store/{id}/bind` | POST | 绑定商家（入驻，一商一店） | ✅ 已编写 |
| 系统 | `/health` | GET | 健康检查 | ✅ 无需文档 |

---

## 三、Auth 认证模块

> 响应格式：**格式 B（AuthResponse）**。密码加密由服务端处理，客户端只传明文。

### 3.1 用户注册

**`POST /api/auth/register`**

注册新用户。密码由服务端 BCrypt 加密存储。

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | ✅ | 用户名，2~50 字符，全局唯一 |
| password | string | ✅ | 密码，至少 6 位且包含字母和数字 |
| nickname | string | ❌ | 昵称，缺省时默认为用户名 |

请求示例：

```json
{
  "username": "alice",
  "password": "alice123",
  "nickname": "Alice"
}
```

成功响应（200）：

```json
{
  "success": true,
  "message": "操作成功",
  "id": 6,
  "username": "alice",
  "nickname": "Alice",
  "totalSpent": 0.0,
  "memberLevel": "普通会员"
}
```

失败响应：

| 场景 | 响应示例 |
|---|---|
| 密码不足 6 位 | `{"success":false,"message":"密码至少需要6个字符",...}` |
| 密码无字母 | `{"success":false,"message":"密码必须包含至少一个字母",...}` |
| 密码无数字 | `{"success":false,"message":"密码必须包含至少一个数字",...}` |
| 用户名已存在 | `{"success":false,"message":"用户名已存在",...}` |

> 注意：失败响应的 `success` 为 `false`，其余字段为 `null`。

### 3.2 用户登录

**`POST /api/auth/login`**

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | ✅ | 用户名 |
| password | string | ✅ | 密码（明文） |

请求示例：

```json
{
  "username": "alice",
  "password": "alice123"
}
```

成功响应（200）：

```json
{
  "success": true,
  "message": "操作成功",
  "id": 6,
  "username": "alice",
  "nickname": "Alice",
  "totalSpent": 0.0,
  "memberLevel": "普通会员"
}
```

失败响应：

| 场景 | 响应示例 |
|---|---|
| 用户名或密码错误（含用户不存在） | `{"success":false,"message":"用户名或密码错误",...}` |

> 安全说明：用户名不存在与密码错误返回同一文案，防止账号枚举。历史明文密码用户首次登录成功后自动升级为 BCrypt（无感知）。

### 3.3 忘记密码

**`POST /api/auth/forgot-password`**

申请密码重置令牌。令牌 30 分钟内有效、一次性使用。

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | ✅ | 用户名 |

请求示例：

```json
{
  "username": "alice"
}
```

成功响应（200）：

```json
{
  "success": true,
  "message": "重置令牌已生成",
  "token": "H95bco9tL6myaMn4s_WFvY6MLunpOf8pnHy6qzWrc8k"
}
```

用户不存在时（200，统一文案防枚举）：

```json
{
  "success": true,
  "message": "重置令牌已生成",
  "token": "如果该账号存在，重置链接已生成（令牌有效期 30 分钟）"
}
```

参数错误（400，Result 格式）：

```json
{
  "code": 400,
  "message": "请输入用户名",
  "data": null
}
```

> 安全说明：申请重置会作废该用户之前所有未过期的令牌。生产环境应改为邮件/短信下发令牌，当前版本直接返回。

### 3.4 重置密码

**`POST /api/auth/reset-password`**

使用令牌设置新密码。成功后令牌立即作废，不能复用。

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| token | string | ✅ | 忘记密码接口返回的令牌 |
| newPassword | string | ✅ | 新密码，强度要求同注册 |

请求示例：

```json
{
  "token": "H95bco9tL6myaMn4s_WFvY6MLunpOf8pnHy6qzWrc8k",
  "newPassword": "newpwd123"
}
```

成功响应（200）：

```json
{
  "success": true,
  "message": "操作成功",
  "id": 6,
  "username": "alice",
  "nickname": "Alice",
  "totalSpent": 0.0,
  "memberLevel": "普通会员"
}
```

失败响应：

| 场景 | 响应示例 |
|---|---|
| 令牌为空 | `{"success":false,"message":"令牌不能为空",...}` |
| 令牌无效/过期/已使用 | `{"success":false,"message":"令牌无效或已过期",...}` |
| 新密码强度不足 | `{"success":false,"message":"密码必须包含至少一个字母",...}` |

### 3.5 获取用户信息

**`GET /api/auth/user/{id}`**

路径参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | number | ✅ | 用户 ID（路径） |

成功响应（200）：

```json
{
  "success": true,
  "message": "操作成功",
  "id": 1,
  "username": "Kaoyanjuan",
  "nickname": "",
  "totalSpent": 181.0,
  "memberLevel": "VIP会员"
}
```

失败响应：

| 场景 | 响应示例 |
|---|---|
| 用户不存在 | `{"success":false,"message":"用户不存在",...}` |

### 3.6 AuthResponse 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| success | boolean | 是否成功 |
| message | string | 提示信息 |
| id | number/null | 用户 ID，失败时为 null |
| username | string/null | 用户名 |
| nickname | string/null | 昵称 |
| totalSpent | number/null | 累计消费金额（元） |
| memberLevel | string/null | 会员等级文案：普通会员（<100）/ VIP会员（≥100）/ SVIP会员（≥500） |

---

## 四、新增接口流程

1. 在后端对应模块实现接口
2. 在本文档"接口总览"表中登记一行，状态标为"✅ 已编写"
3. 在对应模块章节按以下模板补充接口详情：

```markdown
### x.y 接口名称

**`METHOD /api/path`**

一句话功能描述。

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| ... | ... | ... | ... |

请求示例：...

成功响应（200）：...

失败响应：...（表格）
```

4. 递增文档版本号，更新"最后更新"日期

---

## 五、座位模块

> 响应格式：**格式 B（直接 `SeatResponse` 对象）**，与 Auth 模块一致——成功时直接返回业务对象，失败时返回 `Result.error`（`{"code":400,"message":"...","data":null}`）。前端 axios 拦截器已统一解包，无需关注差异。
>
> 座位编号规则：`店名-3位编号`，如 `静安店-001`。全店 99 张桌：001-070 双人桌（2 人）、071-090 四人桌（4 人）、091-099 多人桌（8 人，可坐 6-8 人）。首次启动时自动初始化。
>
> 二维码内容：系统落座页 URL（`{qrBaseUrl}/?seat=座位编号`），由 ZXing 3.5.3 生成 PNG base64 图片。扫码打开系统页面后自动进入落座确认流程。**`qr-base-url` 必须配置为手机可访问的地址**（开发环境为电脑局域网 IP，生产环境为部署域名），配置 `localhost` 会导致手机扫码打不开。

### 5.1 分配座位

**`POST /api/seat/assign`**

按就餐人数从空闲座位中分配最合适的桌型（2 人→双人桌，3-4 人→四人桌，5-8 人→多人桌），并返回落座二维码。并发安全：乐观锁保证同一座位不会分给两批客人。

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| peopleCount | int | ✅ | 就餐人数，1-8 |
| userId | number/null | ❌ | 登录用户 ID |
| guestId | string/null | ❌ | 游客标识（游客必传其一） |

成功响应（200）：

```json
{
  "seatId": 1,
  "storeName": "静安店",
  "seatNo": "001",
  "code": "静安店-001",
  "capacity": 2,
  "status": "ASSIGNED",
  "qrContent": "http://192.168.31.42:5173/?seat=%E9%9D%99%E5%AE%89%E5%BA%97-001",
  "qrBase64": "data:image/png;base64,iVBORw0KGgoAAA..."
}
```

失败响应：

| 场景 | code | message |
|---|---|---|
| 人数超出 1-8 | 400 | 就餐人数需在 1-8 人之间 |
| 无空闲座位 | 500 | 当前没有可用的座位，请稍后再试 |

### 5.2 解析二维码

**`GET /api/seat/resolve?code=座位编号`**

解析二维码内容（座位编号），返回座位当前状态，用于扫码落座前的校验。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | string | ✅ | 座位编号，如 `静安店-001`（URL 编码） |

成功响应（200）：同 5.1 的结构，`qrContent`/`qrBase64` 为 null，`status` 反映当前状态。

失败响应：

| 场景 | code | message |
|---|---|---|
| 编号格式错误 | 400 | 座位编号格式不正确，应为 店名-编号 |
| 店名不匹配 | 400 | 无法识别的座位编号 |
| 座位不存在 | 400 | 座位不存在 |

### 5.3 确认落座

**`POST /api/seat/{id}/occupy`**

扫码后确认落座，状态 `ASSIGNED → OCCUPIED`。支持跨设备：任何设备扫到"已分配"状态的座位均可落座（座位编号即凭证，门店场景下用户电脑取号后拿手机扫码落座）。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | number | ✅ | 座位 ID（路径） |
| userId | number/null | ❌ | 登录用户 ID（当前不参与校验，保留字段） |
| guestId | string/null | ❌ | 游客标识（当前不参与校验，保留字段） |

成功响应（200）：`status` 为 `OCCUPIED`。

失败响应：

| 场景 | code | message |
|---|---|---|
| 座位已被他人占用 | 400 | 该座位已被占用 |
| 座位未分配/已超时释放 | 400 | 该座位未分配或已超时释放，请重新取号 |

### 5.4 离座释放

**`POST /api/seat/{id}/leave`**

用户离座后释放座位，状态 `OCCUPIED → FREE`。已分配（ASSIGNED）但未落座的座位由后端定时任务自动释放（默认 15 分钟）。

成功响应（200）：`status` 为 `FREE`。

### 5.5 全部座位状态

**`GET /api/seat/list`**

管理用：返回全部 99 张座位的状态列表。

成功响应（200）：返回 `SeatResponse[]` 数组。

### 5.6 座位状态与配置

| 状态 | 说明 | 流转 |
|---|---|---|
| FREE | 空闲 | → ASSIGNED（分配） |
| ASSIGNED | 已分配，等待落座 | → OCCUPIED（落座）/ FREE（15 分钟超时自动释放） |
| OCCUPIED | 已落座 | → FREE（用户离座） |

`application.yml` 可配置（前缀 `coffee.seat`）：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| coffee.seat.store-name | 静安店 | 店名，座位编号前缀 |
| coffee.seat.qr-base-url | http://localhost:5173 | 二维码内容基础地址（生产改部署域名） |
| coffee.seat.assign-timeout-minutes | 15 | 分配后未落座的自动释放时长 |
| coffee.seat.two-seats / four-seats / multi-seats | 70 / 20 / 9 | 各桌型数量 |

---

## 六、店铺模块

> 多店扩展：店铺 + 商家两套体系。商家与顾客账号完全隔离（`merchant` 表 vs `coffee_user` 表），**一商一店**：一个商家入驻一家店（店铺归商家管理，入驻后不可再入驻其他店）。
>
> 商家编号规则：注册时服务端自动生成 `sj-{时间戳后 6 位}`（如 `sj-410343`），作为商家登录账号。
>
> 响应格式：店铺接口成功直接返回 `StoreResponse`（格式 B）；商家接口成功直接返回 `MerchantResponse`（含 `success` 字段，业务失败时 `success=false` + `message`）；参数/业务异常由全局处理器统一返回 `{"code":400,"message":"...","data":null}`。
>
> 鉴权说明：与现有系统一致，暂无 token 会话机制，商家登录后由前端持有 `merchantId` 调用后续接口。

### 6.1 商家注册

**`POST /api/merchant/register`**

请求体（`MerchantRegisterRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| password | string | ✅ | 密码，至少 6 位，服务端 BCrypt(12) 加密存储 |
| nickname | string | ❌ | 昵称 |
| phone | string | ❌ | 联系电话 |

> 商家编号（`merchantNo`）无需提交，由服务端自动生成 `sj-{时间戳后 6 位}`，注册成功后返回，作为登录账号。

成功响应（200）：

```json
{"success":true,"message":"操作成功","id":2,"merchantNo":"sj-410343","nickname":"静安店老板","phone":"13800000001","status":"ACTIVE"}
```

失败：`success=false`，如 `密码至少 6 位`。

### 6.2 商家登录

**`POST /api/merchant/login`**

请求体（`MerchantLoginRequest`）：`merchantNo`（商家编号，如 `sj-410343`）/ `password`。

成功响应（200）：同 6.1 的 `MerchantResponse`。

失败场景：

| 场景 | 返回 |
|---|---|
| 编号或密码错误 | `{"success":false,"message":"商家编号或密码错误",...}` |
| 账号已被禁用 | `{"success":false,"message":"账号已被禁用",...}` |

### 6.3 商家信息

**`GET /api/merchant/{id}`**

返回指定商家信息（`MerchantResponse`）。

### 6.4 商家名下店铺（我的店铺）

**`GET /api/merchant/{id}/stores`**

返回该商家绑定的全部店铺（`StoreResponse[]`，按 id 升序）。商家界面"我的店铺"入口。

### 6.5 创建店铺

**`POST /api/store`**

请求体（`StoreRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | string | ✅ | 店铺编码（唯一，如 jingan） |
| name | string | ✅ | 店名（如 静安店） |
| address | string | ❌ | 地址 |
| phone | string | ❌ | 联系电话 |
| businessHours | string | ❌ | 营业时间（如 08:00-22:00） |
| status | enum | ❌ | 营业状态 `OPEN`/`CLOSED`，默认 `OPEN` |
| merchantId | number | ❌ | 创建时直接绑定的商家 id（一商一店：商家已入驻其他店时 400 `该商家已入驻其他店铺`） |

成功响应（200）：`StoreResponse`。失败：`店铺编码已存在`（400）。

### 6.6 全部店铺列表

**`GET /api/store/list`**

用户端选店入口：返回全部店铺（`StoreResponse[]`，按 id 升序）。首次启动自动初始化 20 家种子店铺（`StoreDataInitializer`，表为空时插入）。

### 6.7 店铺详情

**`GET /api/store/{id}`**

返回单个店铺。店铺不存在时 400 `店铺不存在`。

### 6.8 更新店铺

**`PUT /api/store/{id}`**

请求体（`StoreRequest`）：`name`/`address`/`phone`/`businessHours`/`status` 可更新（传 null 不修改）；`code` 不可修改。

### 6.9 删除店铺

**`DELETE /api/store/{id}`**

物理删除。店铺不存在时 400。

### 6.10 绑定商家（入驻）

**`POST /api/store/{id}/bind?merchantId={merchantId}`**

商家入驻已有店铺（如从 21 家种子门店中选一家开店）。**一商一店**：

| 场景 | 返回 |
|---|---|
| 店铺已被其他商家入驻 | 400 `该店铺已被其他商家入驻` |
| 商家已入驻其他店铺 | 400 `该商家已入驻其他店铺` |
| 商家不存在 | 400 `商家不存在` |

### 6.11 可入驻店铺列表

**`GET /api/store/available`**

商家入驻选择：返回全部 `merchant_id` 为空的店铺（`StoreResponse[]`，按 id 升序）。21 家种子店中未被入驻的都在这里。

### 6.12 表结构与状态

`store` 表：`id / code(唯一) / name / address / phone / business_hours / status / merchant_id / created_at / updated_at`，`status` 取值 `OPEN`（营业中）/`CLOSED`（打烊）。

`merchant` 表：`id / merchant_no(唯一, sj-开头) / username(兼容旧数据) / password(BCrypt) / nickname / phone / status / created_at / updated_at`，`status` 取值 `ACTIVE`（正常）/`DISABLED`（禁用）。

> 商家入驻流程：注册（自动获得 `merchantNo`）→ 登录 → 选择入驻 → 入驻现有店铺（`bind`）或开新店（`create` 带 `merchantId`）→ 一商一店，入驻后不可再选其他店。

> 与座位模块的关系：当前 `seat` 表仍为单店配置（`store_name`），多店座位归属改造待商家界面设计后统一进行。
