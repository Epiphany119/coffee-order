# FIKA 咖啡点单系统 — 接口文档

> 本文档为系统唯一接口权威文档。新增接口时必须同步补充至对应模块章节，并更新"接口总览"。

## 文档信息

| 项目 | 内容 |
|---|---|
| 文档版本 | v1.3.0 |
| 最后更新 | 2026-08-06 |
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
| Auth | `/api/auth/user/{id}/preference` | GET | 用户店铺偏好（上次选店，用于恢复店铺） | ✅ 已编写 |
| Auth | `/api/auth/user/{id}/preference` | PUT | 保存店铺偏好 | ✅ 已编写 |
| 游客 | `/api/guest/session` | POST | 签发游客身份 guestId（未登录用户） | ✅ 已编写 |
| 菜单 | `/api/menu` | GET | 获取菜单（上架商品+规格+类目，按店过滤） | ✅ 已编写 |
| 菜单 | `/api/store/{storeId}/menu` | GET | 商家菜单列表（含下架商品） | ✅ 已编写 |
| 菜单 | `/api/store/{storeId}/menu` | POST | 商家新增商品（店内 code 唯一） | ✅ 已编写 |
| 菜单 | `/api/store/{storeId}/menu/{productId}` | PUT | 商家更新商品（改价/上下架） | ✅ 已编写 |
| 菜单 | `/api/store/{storeId}/menu/image` | POST | 商家上传商品图片（multipart，返回可访问 URL） | ✅ 已编写 |
| 菜单 | `/api/store/{storeId}/categories` | GET | 店铺可见类目（共享类目 + 本店自定义类目） | ✅ 已编写 |
| 菜单 | `/api/store/{storeId}/category` | POST | 商家新建自定义类目（仅本店可见） | ✅ 已编写 |
| 订单 | `/api/order` | POST | 创建订单（支持批量，含取餐方式/备注） | ✅ 已编写 |
| 订单 | `/api/orders/user/{userId}` | GET | 用户订单列表 | ✅ 已编写 |
| 订单 | `/api/orders/guest/{guestId}` | GET | 游客订单列表 | ✅ 已编写 |
| 订单 | `/api/orders` | GET | 全部订单（按店/状态过滤） | ✅ 已编写 |
| 订单 | `/api/orders/{id}/action` | POST | 商家操作订单状态（接单/完成/取消，校验跨店） | ✅ 已编写 |
| 订单 | `/api/order/user/{id}/action` | POST | 用户端更新订单状态 | ✅ 已编写 |
| 订单 | `/api/order/guest/{id}/action` | POST | 游客端更新订单状态 | ✅ 已编写 |
| 会员 | `/api/member/{userId}/dashboard` | GET | 会员中心数据 | ✅ 已编写 |
| 收藏 | `/api/favorites?userId={id}` | GET | 收藏列表（userId/guestId 二选一） | ✅ 已编写 |
| 收藏 | `/api/favorites` | POST | 添加收藏（userId/guestId 二选一） | ✅ 已编写 |
| 收藏 | `/api/favorites?productCode={code}` | DELETE | 取消收藏 | ✅ 已编写 |
| 收藏 | `/api/favorites/merge` | POST | 游客收藏合并到用户账号（登录后） | ✅ 已编写 |
| 会员体系 | `/api/membership/card` | GET | 查询会员卡（未开卡返回 null） | ✅ 已编写 |
| 会员体系 | `/api/membership/card/init` | POST | 开卡（幂等，从用户消费快照初始化） | ✅ 已编写 |
| 会员体系 | `/api/membership/benefits` | GET | 会员权益列表（等级折扣+权益券） | ✅ 已编写 |
| 会员体系 | `/api/membership/level-rules` | GET | 等级规则（阈值/折扣） | ✅ 已编写 |
| 会员体系 | `/api/membership/redeem-items` | GET | 积分兑换项列表 | ✅ 已编写 |
| 会员体系 | `/api/membership/points/redeem` | POST | 积分兑换（拆券发放到卡券包） | ✅ 已编写 |
| 会员体系 | `/api/membership/vouchers` | GET | 用户卡券包 | ✅ 已编写 |
| 座位 | `/api/seat/assign` | POST | 按人数分配座位（返回落座二维码） | ✅ 已编写 |
| 座位 | `/api/seat/resolve` | GET | 解析二维码内容（座位编号） | ✅ 已编写 |
| 座位 | `/api/seat/{id}/occupy` | POST | 确认落座 | ✅ 已编写 |
| 座位 | `/api/seat/{id}/leave` | POST | 离座释放 | ✅ 已编写 |
| 座位 | `/api/seat/list` | GET | 全部座位状态（管理用） | ✅ 已编写 |
| 座位 | `/api/seat/occupied` | GET | 按身份查当前店已落座座位（幽灵占座恢复） | ✅ 已编写 |
| 商家 | `/api/merchant/register` | POST | 商家注册（自动生成 sj-编号） | ✅ 已编写 |
| 商家 | `/api/merchant/login` | POST | 商家登录（商家编号+密码） | ✅ 已编写 |
| 商家 | `/api/merchant/{id}` | GET | 商家信息 | ✅ 已编写 |
| 商家 | `/api/merchant/{id}/stores` | GET | 商家名下店铺（入驻状态） | ✅ 已编写 |
| 商家 | `/api/merchant/{id}/dashboard` | GET | 商家经营数据工作台（商家后台首页） | ✅ 已编写 |
| 店铺 | `/api/store` | POST | 创建店铺（开新店） | ✅ 已编写 |
| 店铺 | `/api/store/list` | GET | 全部店铺列表 | ✅ 已编写 |
| 店铺 | `/api/store/available` | GET | 可入驻店铺列表 | ✅ 已编写 |
| 店铺 | `/api/store/open` | GET | 营业中店铺列表（用户端选店） | ✅ 已编写 |
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

### 3.6 用户店铺偏好

**`GET /api/auth/user/{id}/preference`**、**`PUT /api/auth/user/{id}/preference`**

记录用户上次选择的店铺，登录后前端据此自动恢复店铺（配套 `GET /api/store/open` 校验店铺仍营业）。

GET 成功响应（200）：

```json
{ "success": true, "lastStoreId": 1 }
```

PUT 请求体：`{ "storeId": 1 }`（传 null 表示清除偏好）。成功响应：`{ "success": true, "message": "偏好已保存" }`。

### 3.7 签发游客身份

**`POST /api/guest/session`**

未登录用户获取游客标识 `guestId`（格式 `g-{uuid}`）。游客的下单、收藏、座位数据均按 `guestId` 入库隔离；游客登录后通过收藏合并接口（见 9.5）把收藏迁移到用户账号。前端内存持有，不落浏览器存储。

成功响应（200）：

```json
{ "success": true, "guestId": "g-1a2b3c4d5e6f7a8b" }
```

### 3.8 AuthResponse 字段说明

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
> 座位编号规则：`店名-3位编号`，店名统一为 `store.name`，如 `Fika・静安店-001`。每家营业中店铺 99 张桌：001-070 双人桌（2 人）、071-090 四人桌（4 人）、091-099 多人桌（8 人，可坐 6-8 人）。启动时按店自动补齐（已存在座位的店跳过），`SeatResponse` 含 `storeId` 用于前端校验座位归属店铺。
>
> 座位按店隔离：分配（assign）与列表（list）均按 `storeId` 过滤，二维码/扫码落座按编号解析（编号自带店名，无需额外传店）。
>
> 二维码内容：系统落座页 URL（`{qrBaseUrl}/?seat=座位编号`），由 ZXing 3.5.3 生成 PNG base64 图片。扫码打开系统页面后自动进入落座确认流程。**`qr-base-url` 必须配置为手机可访问的地址**（开发环境为电脑局域网 IP，生产环境为部署域名），配置 `localhost` 会导致手机扫码打不开。

### 5.1 分配座位

**`POST /api/seat/assign`**

按就餐人数从空闲座位中分配最合适的桌型（2 人→双人桌，3-4 人→四人桌，5-8 人→多人桌），并返回落座二维码。并发安全：乐观锁保证同一座位不会分给两批客人。

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| storeId | number | ✅ | 店铺 id（用户端当前店铺，座位按店隔离） |
| peopleCount | int | ✅ | 就餐人数，1-8 |
| userId | number/null | ❌ | 登录用户 ID |
| guestId | string/null | ❌ | 游客标识（游客必传其一） |

成功响应（200）：

```json
{
  "seatId": 1,
  "storeId": 1,
  "storeName": "Fika・静安店",
  "seatNo": "001",
  "code": "Fika・静安店-001",
  "capacity": 2,
  "status": "ASSIGNED",
  "assignedUserId": null,
  "assignedGuestId": "g-abc123",
  "assignedAt": "2026-08-06T10:30:00",
  "occupiedAt": null,
  "qrContent": "http://192.168.31.42:5173/?seat=Fika%E3%83%BB%E9%9D%99%E5%AE%89%E5%BA%97-001",
  "qrBase64": "data:image/png;base64,iVBORw0KGgoAAA..."
}
```

> `assignedUserId`/`assignedGuestId`：占用者标识（null=无）；`assignedAt`/`occupiedAt`：分配/占用时间，商家端展示座位占用时长用（null=未发生）。

失败响应：

| 场景 | code | message |
|---|---|---|
| 未传 storeId | 400 | 请先选择店铺 |
| 人数超出 1-8 | 400 | 就餐人数需在 1-8 人之间 |
| 无空闲座位 | 500 | 当前没有可用的座位，请稍后再试 |

### 5.2 解析二维码

**`GET /api/seat/resolve?code=座位编号`**

解析二维码内容（座位编号），返回座位当前状态，用于扫码落座前的校验。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | string | ✅ | 座位编号，如 `Fika・静安店-001`（URL 编码） |

成功响应（200）：同 5.1 的结构，`qrContent`/`qrBase64` 为 null，`status` 反映当前状态。

失败响应：

| 场景 | code | message |
|---|---|---|
| 编号格式错误 | 400 | 座位编号格式不正确，应为 店名-编号 |
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

管理用：返回店铺座位状态列表，可按店铺过滤。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| storeId | number | ❌ | 店铺 id，传了只返回该店座位（每店 99 张），不传返回全部 |

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
| coffee.seat.qr-base-url | http://localhost:5173 | 二维码内容基础地址（生产改部署域名） |
| coffee.seat.assign-timeout-minutes | 15 | 分配后未落座的自动释放时长 |
| coffee.seat.two-seats / four-seats / multi-seats | 70 / 20 / 9 | 各桌型数量（每家店） |

### 5.7 按身份查询已落座座位（幽灵占座恢复）

**`GET /api/seat/occupied?storeId={storeId}&userId={userId}&guestId={guestId}`**

用户端本地座位记录因切换店铺/退出登录被清除后，通过该接口找回自己在当前店已落座的座位（防止再次取号造成重复占座）。前端在"本地无座位"路径（进主页/切店/重登）先调用本接口，有则直接恢复显示，无才弹取号框。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| storeId | number | ✅ | 当前店铺 id（座位按店隔离，只查本店） |
| userId | number | ❌ | 登录用户 id（与 guestId 至少传其一） |
| guestId | string | ❌ | 游客标识（与 userId 至少传其一） |

成功响应（200）：`SeatResponse[]`——该店中该身份 `OCCUPIED` 的座位，按 `occupiedAt` 倒序（正常情况下最多一张；无占用返回空数组）。跨店隔离：在其他店占的座不会返回。

### 5.8 座位表结构（三表重构）

座位按店隔离，由三张表支撑（2026-08-06 重构，备份表 `seat_backup_20260806`）：

| 表 | 说明 |
|---|---|
| `seat_template` | 桌型模板，99 行（001-070 双人桌 / 071-090 四人桌 / 091-099 多人桌），全店共用：`id / seat_no(唯一) / capacity / type_name / created_at` |
| `store` | 21 家店铺（见六、店铺模块），座位编号前缀统一用 `store.name` |
| `seat` | 店铺座位实例：`id / store_id / template_id / status / assigned_user_id / assigned_guest_id / assigned_at / occupied_at / created_at / updated_at`，`(store_id, template_id)` 唯一索引 `uk_seat_store_template` |

> 展示字段（`storeName`/`seatNo`/`capacity`）不再冗余存储，查询时 JOIN `store` + `seat_template` 补全。启动时 `SeatDataInitializer` 为每张 `status = OPEN` 的店铺按模板自动补齐座位（已有座位的店跳过）。

---

## 六、店铺模块

> 多店扩展：店铺 + 商家两套体系。商家与顾客账号完全隔离（`merchant` 表 vs `coffee_user` 表），**一商一店**：一个商家入驻一家店（店铺归商家管理，入驻后不可再入驻其他店）。
>
> 商家编号规则：注册时服务端自动生成 `sj-{时间戳后 6 位}`（如 `sj-410343`），作为商家登录账号。
>
> **占位商家机制**：系统为每家门店预分配一条占位商家记录（`merchant_no` 预生成、`username/password` 等信息为空、`status=DISABLED`，`store_name`/`store_id` 关联门店）。商家入驻现有店铺 = 激活该店占位记录（填入资料、置 ACTIVE、绑定店铺），并以预分配的编号登录；`coffee_user.merchant_no` 同步回填（一个用户端账号只能绑定一家店）。开新店模式则不占位（注册生成独立档案，之后创建店铺）。
>
> 响应格式：店铺接口成功直接返回 `StoreResponse`（格式 B）；商家接口成功直接返回 `MerchantResponse`（含 `success` 字段，业务失败时 `success=false` + `message`）；参数/业务异常由全局处理器统一返回 `{"code":400,"message":"...","data":null}`。
>
> 鉴权说明：与现有系统一致，暂无 token 会话机制，商家登录后由前端持有 `merchantId` 调用后续接口。

### 6.1 商家注册（入驻）

**`POST /api/merchant/register`**

请求体（`MerchantRegisterRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | ✅ | 用户端账号（`coffee_user.username`），须为已注册的用户端用户，且未注册过商家（用户名查重） |
| password | string | ✅ | 须与用户端登录密码一致（系统校验 BCrypt 匹配），商家登录密码默认沿用用户端密码 |
| nickname | string | ❌ | 昵称 |
| phone | string | ❌ | 联系电话 |
| storeId | number | ❌ | 入驻现有店铺时必填：激活该店预分配的占位商家记录并绑定（店铺须未入驻且已初始化占位档案）；为空 = 开新店模式（生成独立商家档案，不绑定店铺） |

> 注册流程：提交资料 → 审核 → 分配商家编号与初始密码。当前系统审核默认直接通过（`status=ACTIVE`），流程与校验保留。
> 商家编号（`merchantNo`）无需提交：入驻现有店铺时复用该店**预分配的占位编号**（如 `sj-318967`）；开新店模式由服务端自动生成 `sj-{时间戳后 6 位}`。注册成功后返回，作为商家登录账号。
> 校验规则：① 用户名须存在于 `coffee_user`；② 用户名不可重复注册商家（`merchant.username` 唯一）；③ 密码须与用户端登录密码一致。
> 入驻现有店铺额外校验：店铺不存在 / 已被其他商家入驻 / 未初始化占位档案 / 用户端账号已绑定商家（`coffee_user.merchant_no` 非空）时 400 拒绝。
> 入驻成功后：占位记录填入资料并置 `ACTIVE`，`store.merchant_id` 指向该商家，`coffee_user.merchant_no` 回填商家编号，`/api/store/available` 不再返回该店。
> 店铺营业状态：新入驻默认**打烊**（`CLOSED`），由商家在后台手动切换营业/打烊（`PUT /api/store/{id}` 传 `status`）；未入驻店铺一律打烊，用户端选店（`GET /api/store/open`）只展示营业中的店铺。

成功响应（200）：

```json
{"success":true,"message":"操作成功","id":7,"merchantNo":"sj-318967","nickname":"测试老板","phone":"13800000001","storeName":"Fika・衡山路店","status":"ACTIVE"}
```

失败：`success=false`，如 `用户名不存在，请先在用户端注册该账号` / `该用户名已注册过商家` / `密码与用户端登录密码不一致` / `密码至少 6 位`；入驻场景另有 `店铺不存在` / `该店铺已被其他商家入驻` / `该店铺未初始化商家档案，请联系管理员` / `该用户已绑定商家 sj-xxx，一个用户端账号只能入驻一家店`。

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

管理/选店入口：返回全部店铺（`StoreResponse[]`，按 id 升序）。首次启动自动初始化 21 家种子店铺（`StoreDataInitializer`，表为空时插入）。

### 6.6.1 营业中店铺列表（用户端选店）

**`GET /api/store/open`**

用户端左上角"切换店铺"入口：只返回 `status = OPEN` 的营业中店铺（`StoreResponse[]`，按 id 升序）。用户切换店铺后，点单、下单（`storeId`）、座位（`assign`/`list` 按店隔离）均绑定该店。切换时前端清空上一家店的购物袋与座位记录。

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

> 前端入驻现有店铺现走 `register` + `storeId`（见 6.1，激活该店占位商家记录）；`bind` 接口保留兼容（旧流程/直接绑定商家 id）。

### 6.11 可入驻店铺列表

**`GET /api/store/available`**

商家入驻选择：返回全部 `merchant_id` 为空的店铺（`StoreResponse[]`，按 id 升序）。21 家种子店中未被入驻的都在这里。

### 6.12 表结构与状态

`store` 表：`id / code(唯一) / name / address / phone / business_hours / status / merchant_id / created_at / updated_at`，`status` 取值 `OPEN`（营业中）/`CLOSED`（打烊）。**未入驻店铺一律打烊，入驻后由商家在后台手动切换营业状态**（`PUT /api/store/{id}`）；用户端选店只展示 `OPEN` 店铺。

`merchant` 表：`id / merchant_no(唯一, sj-开头) / username(可空, 兼容旧数据, 占位记录为空) / password(可空, BCrypt, 占位记录为空) / nickname / phone / store_name(绑定的店名) / store_id(绑定的店铺 id) / status / created_at / updated_at`，`status` 取值 `ACTIVE`（正常）/`DISABLED`（禁用/占位打烊）。

`coffee_user` 表补充列：`merchant_no`（该用户端账号绑定的商家编号，null = 未入驻；一账号一店）。

> 商家入驻流程：注册（填用户端账号+密码，可选入驻店铺）→ 入驻现有店铺（`register` 带 `storeId`，激活该店占位商家记录并绑定）或开新店（`register` 不带 `storeId` → `create` 带 `merchantId`）→ 一商一店，入驻后不可再选其他店。
> 占位商家记录：每家门店一条（`merchant_no` 预分配、信息空、`status=DISABLED`），入驻 = 激活；占位记录不可登录（密码为空一律拒绝）。

> 座位已按店隔离（三表结构见 5.7），分配/列表按 `storeId` 过滤，编号前缀为 `store.name`。

### 6.13 商家经营数据工作台

**`GET /api/merchant/{merchantId}/dashboard`**

商家后台首页数据源：今日营业额/订单数、待处理订单数、近 7 天销售曲线、最近 5 笔订单。商家未入驻店铺时返回 404 `该商家尚未入驻店铺`。

成功响应（200）：

```json
{
  "store": { "storeId": 1, "code": "lujiazui", "name": "Fika・陆家嘴店", "status": "OPEN", "...": "..." },
  "todayRevenue": 128.5,
  "todayOrders": 6,
  "pendingOrders": 2,
  "weekSales": [ { "day": "07-31", "amount": 86.0 }, { "day": "08-06", "amount": 128.5 } ],
  "recentOrders": [ { "id": 102, "storeId": 1, "fulfillmentType": "PICKUP", "note": "", "beverageName": "拿铁", "status": "PENDING", "finalPrice": 22.0, "createdAt": "2026-08-06T10:30:00", "...": "..." } ]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| store | StoreResponse | 该商家绑定的店铺（一商一店，取第一家） |
| todayRevenue | number | 今日营业额（该店当日 `final_price` 合计） |
| todayOrders | number | 今日订单数 |
| pendingOrders | number | 待处理订单数（status = PENDING） |
| weekSales | array | 近 7 天每日营业额，`{ day: "MM-dd", amount }` 按日期升序 |
| recentOrders | array | 最近 5 笔订单（结构同订单列表项） |

### 6.14 表结构与状态（补充）

商品与分类已重构为 `menu_item` / `menu_category` 两表（详见 7.8 与 `docs/DATABASE.md`），`store_id` 即**归属语义**：`0` = 全局共享（所有店铺可见，仅一份），`N` = 某店铺专属。商品编辑/下架全局共享品时自动**懒复制**为该店专属品再修改（写时复制），只影响本店，其他店仍用全局品；共享记录本身只读（无删除接口）。

---

## 七、菜单与商品模块

> 响应格式：**格式 A（`Result<T>`）**。菜单按店隔离：用户端只看到当前店铺的上架商品，商家端管理本店全部商品（含下架）。

### 7.1 获取菜单（用户端）

**`GET /api/menu?storeId={storeId}`**

用户端菜单数据源：当前店铺的上架商品 + 可选规格 + 店铺可见类目。前端在切换店铺后（`watch currentStore`）调用。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| storeId | number | ❌ | 店铺 id，不传返回空商品列表（`products: []`，categories 为空数组） |

成功响应（200）：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "products": [
      {
        "id": 1, "storeId": 1, "code": "latte", "name": "拿铁",
        "categoryId": 1, "categoryCode": "coffee",
        "basePrice": 22.0, "priceSmall": 18.0, "priceMedium": 22.0, "priceLarge": 26.0,
        "customUnit": "ml", "description": "", "imageUrl": "",
        "temperature": "BOTH", "available": true
      }
    ],
    "sizes": ["SMALL", "MEDIUM", "LARGE"],
    "categories": [
      { "id": 1, "code": "coffee", "name": "咖啡", "icon": "☕", "storeId": 0 },
      { "id": 6, "code": "cus1786015745749", "name": "手冲专区", "icon": "🏷️", "storeId": 5 }
    ]
  }
}
```

> 只返回 `available = true` 的商品；`sizes` 固定为 SMALL/MEDIUM/LARGE；`categories` = 共享类目 + 本店自定义类目（自定义类目 `storeId` 为店铺 id，前端可用于 tab 展示）。

### 7.2 商家菜单列表

**`GET /api/store/{storeId}/menu`**

商家后台"菜单管理"数据源：返回该店全部商品（**含下架**，`available = false` 也在列，供上下架管理）。直接返回 `MenuItemDTO[]`（格式 B，无 Result 包装）。

### 7.3 商家新增商品

**`POST /api/store/{storeId}/menu`**

请求体（`MenuItemRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | string | ✅ | 商品编码，**店内唯一**（跨店可同名）；重复返回 400 `该店已存在此编码的商品: {code}` |
| name | string | ❌ | 商品名称 |
| categoryId | number | ❌ | 分类外键（menu_category.id）。可不传，后端按 categoryCode 反查回填 |
| categoryCode | string | ❌ | 分类编码：coffee / tea / dessert / food / ice / 自定义类目 code（cus 开头） |
| basePrice | number | ❌ | 基础价格（规格价缺失时的兜底价，缺省 0） |
| priceSmall / priceMedium / priceLarge | number | ❌ | 规格定价（小/中/大份，留空回退 basePrice） |
| description | string | ❌ | 描述 |
| imageUrl | string | ❌ | 图片地址（上传接口返回的相对 URL） |
| temperature | string | ❌ | 温度：HOT 热 / COLD 冷 / BOTH 冷热可选 / ROOM 常温 |
| available | boolean | ❌ | 上架状态（新增默认 true） |

成功响应（200）：`MenuItemDTO`（含 `id`/`storeId`）。

> **编辑共享商品（懒复制）**：`code` 对应 `store_id = 0` 的全局共享品时，系统先复制一份 `store_id = 该店` 的副本（保留 categoryId），再在副本上应用本次修改；全局共享记录保持只读。同 code 的新增请求同样返回 400 拒绝。

### 7.4 商家更新商品

**`PUT /api/store/{storeId}/menu/{productId}`**

改价/改描述/上下架/换分类等。请求体同 7.3（`code` 不可改，仅用于识别）。`available = false` 即下架，用户端菜单立即不显示。分类变更：`categoryId` 优先，未传时按 `categoryCode` 反查回填。成功响应（200）：`MenuItemDTO`。

### 7.5 MenuItemDTO 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 商品 id |
| storeId | number | 所属店铺 id（0 = 全局共享品） |
| code | string | 商品编码（店内唯一） |
| name | string | 名称 |
| categoryId | number/null | 分类外键（menu_category.id） |
| categoryCode | string | 分类编码（coffee/tea/dessert/food/ice 或 cus 开头自定义） |
| basePrice | number | 基础价格（规格价缺失时兜底） |
| priceSmall / priceMedium / priceLarge | number/null | 规格定价（小/中/大份，null = 未设置回退 basePrice） |
| customUnit | string | 定制规格单位：coffee/tea/ice → ml，dessert/food → g（用户端定制规格输入用） |
| description / imageUrl | string | 描述 / 图片 |
| temperature | string | HOT/COLD/BOTH/ROOM |
| available | boolean | 上架状态 |

### 7.6 店铺类目管理

**`GET /api/store/{storeId}/categories`**

返回该店可见类目（`MenuCategoryDTO[]`）：共享类目在前（`store_id = 0`），随后本店自定义类目（`store_id = 店铺`），均按 `sort_order, id` 排序。直接返回数组（格式 B）。

**`POST /api/store/{storeId}/category`**

商家新建自定义类目，仅本店可见。请求体（`MenuCategoryRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | string | ✅ | 类目名称（非空，如"手冲专区"） |
| icon | string | ❌ | 图标 emoji，缺省 🏷️ |

服务端自动生成 `code = "cus" + 毫秒时间戳`（全局唯一）并落库 `menu_category`。成功响应（200）：`MenuCategoryDTO`（含新类目 `id`/`code`）。

### 7.7 商家上传商品图片

**`POST /api/store/{storeId}/menu/image`**

multipart/form-data 上传，字段名 `file`。文件落盘 `{user.home}/coffee-uploads/{storeId}/{uuid}.{ext}`。

成功响应（200）：

```json
{ "url": "/uploads/5/7c754b2f20784adba22bd62fa6917b69.png" }
```

> 返回相对 URL，前端拼接后端地址访问；后端已配置静态映射 `/uploads/**` → `{user.home}/coffee-uploads/`。**开发环境 vite 需代理 `/uploads`；生产环境 nginx 需配置 `/uploads` 静态代理，否则图片 404**（常见排查点）。

### 7.8 表结构

`menu_item`（商品表，2026-08 由 `product` 重构）：

| 列 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | 主键 |
| store_id | bigint | 归属：0 = 全局共享品 / N = 该店专属品 |
| code | varchar(50) | 商品编码（店内唯一） |
| name | varchar(50) | 名称 |
| category_id | bigint FK | → menu_category.id（与 categoryCode 保持一致） |
| category_code | varchar(30) | 分类编码 |
| base_price | double | 基础价（规格价兜底） |
| price_small / price_medium / price_large | double | 规格定价（可空） |
| description | varchar(200) | 描述 |
| image_url | varchar(500) | 图片 URL |
| temperature | varchar(20) | 温度（热/冰/常温） |
| available | tinyint(1) | 是否在售 |

`menu_category`（分类表，2026-08 由 `product_category` 重构）：

| 列 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | 主键 |
| code | varchar(30) UNIQUE | 分类编码：内置 5 个（coffee/tea/dessert/food/ice）+ 自定义（cus+时间戳） |
| name | varchar(30) | 分类名称 |
| icon | varchar(10) | 图标 emoji |
| sort_order | int | 排序 |
| store_id | bigint | 归属：0 = 共享类目（所有店可见）/ N = 该商家创建的自定义类目（仅本店可见） |

---

## 八、订单模块

> 响应格式：**格式 A（`Result<T>`）**。订单按店隔离：`user_order` 表含 `store_id`，商家只能操作本店订单。

### 8.1 创建订单

**`POST /api/order`**

支持单杯与批量下单（`items` 非空即批量）。请求体（`CreateOrderCommand`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| userId | number | ⚠️ | 登录用户下单必传（与 guestId 二选一） |
| guestId | string | ⚠️ | 游客下单必传（与 userId 二选一） |
| storeId | number | ✅ | 下单店铺（用户端当前选中店铺） |
| fulfillmentType | string | ❌ | 取餐方式：`PICKUP` 到店自取 / `DINE_IN` 店内用餐 |
| note | string | ❌ | 订单备注 |
| productCode | string | 单品必填 | 商品编码 |
| size | string | ❌ | 规格 SMALL/MEDIUM/LARGE |
| condiments | string[] | ❌ | 加料列表 |
| items | array | 批量必填 | `[{ productCode, size, condiments, quantity }]` |
| couponCode | string | ❌ | 优惠券编码（FIKA8/SWEET12/BEAN15，仅会员有效） |

成功响应（200）：`OrderResponse`（`orderId`/`finalPrice`/`status` 等，含会员折扣/优惠券/积分）。失败：商品不存在 404、会员折扣规则等按全局异常处理。

### 8.2 用户订单列表

**`GET /api/orders/user/{userId}`**

返回该用户全部订单（`Map[]`，按时间倒序）。列表项字段：`id / userId / guestId / storeId / fulfillmentType / note / beverageName / size / condiments / originalPrice / finalPrice / status / createdAt / estimatedReadyTime`。

### 8.3 游客订单列表

**`GET /api/orders/guest/{guestId}`**

同 8.2，按游客标识查。

### 8.4 全部 / 按店订单列表

**`GET /api/orders?storeId={storeId}&status={status}`**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| storeId | number | ❌ | 传了按店过滤（商家后台订单页），不传返回全部 |
| status | string | ❌ | 状态过滤：`PENDING` / `PREPARING` / `COMPLETED` / `CANCELED` |

成功响应（200）：`Map[]`，列表项在 8.2 基础上多 `orderType`（`user` 用户单 / `guest` 游客单）。

### 8.5 商家操作订单状态

**`POST /api/orders/{id}/action?action={action}&storeId={storeId}`**

商家后台订单操作（接单/完成/取消），**必须传 storeId** 校验订单归属。

| 参数 | 说明 |
|---|---|
| action=start | 接单：`PENDING → PREPARING`（制作中） |
| action=complete | 完成：`PREPARING → COMPLETED` |
| action=cancel | 取消：`PREPARING → CANCELED` |

成功响应（200）：`OrderResponse`（`status` 为中文描述，如"制作中"）。失败：

| 场景 | code | message |
|---|---|---|
| 订单不存在 | 404 | 订单不存在 |
| 订单不属于该店铺 | 403 | 订单不属于该店铺，无权操作 |

### 8.6 用户端 / 游客端更新订单状态

**`POST /api/order/user/{id}/action?action={action}`**、**`POST /api/order/guest/{id}/action?action={action}`**

用户/游客对自己的订单做状态操作。成功响应（200）：`OrderResponse`。

### 8.7 订单状态与流转

| 状态（DB） | 中文 | 流转 |
|---|---|---|
| PENDING | 待处理 | → PREPARING（商家接单） |
| PREPARING | 制作中 | → COMPLETED（完成）/ CANCELED（取消） |
| COMPLETED | 已完成 | 终态 |
| CANCELED | 已取消 | 终态 |

---

## 九、会员中心与收藏

### 9.1 会员中心数据

**`GET /api/member/{userId}/dashboard`**

用户端"会员中心"页数据源。用户不存在时按零消费处理，不报错。

成功响应（200）：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "nickname": "Alice",
    "totalSpent": 181.0,
    "memberLevel": "VIP会员",
    "points": 181,
    "pointsLevel": "BRONZE",
    "nextThreshold": 500,
    "amountToNext": 319.0,
    "progress": 36,
    "coupons": [
      { "code": "FIKA8", "name": "下午茶立减 ¥8", "minimum": 48, "discount": 8 }
    ]
  }
}
```

| 字段 | 说明 |
|---|---|
| memberLevel | 普通会员（<100）/ VIP会员（≥100）/ SVIP会员（≥500） |
| pointsLevel | 积分等级 BRONZE 等（预留） |
| nextThreshold / amountToNext / progress | 距下一等级门槛 / 差额 / 升级进度百分比（0-100） |
| coupons | 可用优惠券（当前为固定三张：FIKA8/SWEET12/BEAN15） |

### 9.2 收藏列表

**`GET /api/favorites?userId={userId}`** 或 **`GET /api/favorites?guestId={guestId}`**

返回该身份（登录用户或游客）的收藏商品（`MenuItemDTO[]`，按 id 倒序，含 `storeId`/`available`）。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| userId | number | ⚠️ | 登录用户 id（与 guestId 二选一） |
| guestId | string | ⚠️ | 游客标识（与 userId 二选一） |

### 9.3 添加收藏

**`POST /api/favorites`**

请求体：`{ "userId": 1, "productCode": "latte" }`（userId/guestId 至少一个）。商品不存在时 400。

成功响应（200）：`{ "success": true, "message": "已添加到收藏" }`。

### 9.4 取消收藏

**`DELETE /api/favorites?userId={userId}&productCode={code}`**（或 `guestId={guestId}`）

成功响应（200）：`{ "success": true, "message": "已取消收藏" }`。

### 9.5 游客收藏合并

**`POST /api/favorites/merge`**

游客登录后调用：把该游客标识下的收藏迁移到用户账号（商品并集去重）。请求体：`{ "userId": 1, "guestId": "g-xxx" }`。

成功响应（200）：`{ "success": true, "message": "收藏已合并" }`。

---

## 十、会员体系模块

> 响应格式：**直接返回业务对象（格式 B）**，异常由全局处理器返回 `Result.error`（`{"code":400,"message":"...","data":null}`）。会员卡/权益/积分兑换均为会员体系（membership）模块独立能力，与旧会员中心（九、`/api/member/{id}/dashboard`）并存——dashboard 为只读展示数据源，本模块提供开卡、兑换、卡券包等操作能力。

### 10.1 查询会员卡

**`GET /api/membership/card?userId={userId}`**

未开卡返回 `null`。已开卡返回 `MemberCardDTO`：

```json
{
  "id": 1, "userId": 1, "cardNo": "CARD-8f3a2c", "level": "VIP",
  "points": 181, "totalSpent": 181.0, "exchangePoints": 0,
  "status": 1, "discountRate": 0.9,
  "createdAt": "2026-08-06T10:00:00", "updatedAt": "2026-08-06T10:00:00"
}
```

| 字段 | 说明 |
|---|---|
| level | 等级 REGULAR / VIP / SVIP |
| status | 1 正常 / 0 冻结 |
| discountRate | 当前等级折扣率（0.9 = 9 折） |

### 10.2 开卡

**`POST /api/membership/card/init`**

幂等：已开卡直接返回现有卡。请求体：`{ "userId": 1 }`。从 `coffee_user` 消费记录快照初始化等级与积分。成功响应同 10.1。

### 10.3 会员权益列表

**`GET /api/membership/benefits?userId={userId}`**

返回 `BenefitDTO[]`：按当前等级计算的可享权益（等级折扣 + 权益券），前端会员中心展示。

### 10.4 等级规则

**`GET /api/membership/level-rules`**

返回 `LevelRuleDTO`（前端免硬编码）：`discountRate` 当前折扣率 + `levels[]` 各等级档位（`level`/`label`/`threshold` 消费门槛/`discountRate`）+ `pointsRule` 积分规则说明（如 每消费 1 元积 1 分）。

### 10.5 积分兑换项

**`GET /api/membership/redeem-items`**

返回 `RedeemItemDTO[]`：可兑换项列表（接口下发，前端免硬编码），含 `code`/`name`/`costPoints`（所需积分）/`grants[]`（发放内容，如 `{ name: "¥5 代金券", discount: 5, count: 1 }`）。

### 10.6 积分兑换

**`POST /api/membership/points/redeem`**

请求体：`{ "userId": 1, "itemCode": "xxx" }`。校验积分充足后扣减积分，按 `grants` 发放（券类拆成卡券入卡券包）。成功响应 `RedeemResultDTO`（含发放明细）；积分不足返回 400 `积分不足`。

### 10.7 用户卡券包

**`GET /api/membership/vouchers?userId={userId}`**

返回 `VoucherDTO[]`：该用户持有的全部卡券，字段：`voucherNo`（券码）/`name`（券名）/`discount`（面额）/`minimum`（使用门槛，0=无门槛）/`status`（0 未使用 / 1 已使用 / 2 已过期）/`source`（REDEEM 兑换 / GIFT 赠送）/`createdAt`/`expiresAt`。
