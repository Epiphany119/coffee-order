# FIKA 咖啡点单系统 — 数据库设计

> 数据库：`coffee_order_pro`（MySQL 8.0，utf8mb4 / utf8mb4_unicode_ci）。本文档描述当前线上结构（2026-08-31），结构变更后请同步更新本文档并重新导出 `sql_backup/` 备份。

> 除下方基础业务表外，订单幂等、Outbox、库存、定位、秒杀、Agent、站内通知和外卖配送等运行时表由 `sql/migrations/` 中的版本迁移统一创建；不要依赖应用启动时临时建表。外卖模块新增表由 `V20260831_13_delivery_module.sql` 创建。

## 一、基础业务表总览（25 张）

按业务域分组：

| 域 | 表 | 说明 |
|---|---|---|
| 身份 | `coffee_user` | 用户端账号（顾客）。含累计消费、会员等级、积分、上次选店、绑定商家号 |
| 身份 | `guest` | 游客身份（`guest_id`，如 `g-xxx`），下单/收藏/座位按身份隔离 |
| 身份 | `merchant` | 商家账号（`merchant_no` 以 `sj-` 开头），与顾客账号完全隔离 |
| 店铺 | `store` | 店铺（21 家种子店启动时初始化），`merchant_id` 绑定商家（一商一店） |
| 菜单 | `menu_category` | 商品分类：5 个内置共享类目 + 商家自定义类目（2026-08 由 `product_category` 重构） |
| 菜单 | `menu_item` | 商品：全局共享品（store_id=0）+ 店铺专属品（2026-08 由 `product` 重构） |
| 菜单 | `user_favorite` | 收藏（user_id / guest_id 双轨） |
| 订单 | `user_order` | 订单主表（用户/游客下单统一入此表） |
| 订单 | `order_item` | 订单明细（多商品批量下单） |
| 外卖 | `delivery_address` / `delivery_order` | 顾客地址快照、配送单与配送状态 |
| 外卖 | `delivery_rider` | 配送员账号与抢单身份 |
| 订单 | `review` | 评价（预留） |
| 售后 | `after_sale` | 售后单（用户提交，待商家处理；2026-08-07 新建） |
| 售后 | `feedback` | 订单反馈（评分+建议；2026-08-07 新建） |
| 支付 | `payment` | 支付单（下单即建，Mock 可跑通，微信/支付宝/银行待接入；2026-08-07 新建） |
| 会员体系 | `member_card` | 会员卡（开卡后生效，等级/积分/累计消费快照） |
| 会员体系 | `member_points` | 积分（与 member_card 并存，dashboard 数据源） |
| 会员体系 | `user_voucher` | 用户卡券包（兑换所得，含状态/来源/有效期） |
| 会员体系 | `coupon` | 优惠券定义（旧体系，固定三张：FIKA8/SWEET12/BEAN15） |
| 座位 | `seat_template` | 桌型模板（99 行/店，全店共用） |
| 座位 | `seat` | 店铺座位实例（按店隔离） |
| 认证 | `password_reset_token` | 密码重置令牌（一次性、30 分钟有效） |
| 购物车 | `cart` / `cart_item` | 购物车（旧体系预留，当前前端购物车为内存态，未落库） |
| 遗留 | `coffee_beverage` | 旧装饰器模式饮品表（重构后未使用） |
| 遗留 | `guest_order` | 旧游客订单表（重构后订单统一入 `user_order`） |

## 二、核心表结构

### 2.1 menu_item — 商品表（核心）

| 列 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | 主键 |
| store_id | bigint | **归属**：`0` = 全局共享品（所有店可见，仅一份）/ `N` = 该店专属品 |
| code | varchar(50) | 商品编码（**店内唯一**；共享品间全局唯一） |
| name | varchar(50) | 名称 |
| category_id | bigint FK | → `menu_category.id`（与 category_code 保持一致，旧数据已回填） |
| category_code | varchar(30) | 分类编码 |
| base_price | double | 基础价（规格价缺失时兜底） |
| price_small / price_medium / price_large | double | 规格定价（小/中/大份，可空） |
| description | varchar(200) | 描述 |
| image_url | varchar(500) | 图片相对 URL（`/uploads/{storeId}/{文件名}`） |
| temperature | varchar(20) | HOT 热 / COLD 冷 / BOTH 冷热可选 / ROOM 常温 |
| available | tinyint(1) | 1 上架 / 0 下架 |
| topup | tinyint(1) | **凑单标记**（2026-08-07 新增）：1 = 凑单推荐品（配料/小料/小饮品/试吃品），默认 0。购物袋凑单弹窗只推荐 `topup=1` 且上架、最低可买价 ≤ 还差金额的商品 |

索引：`category_id`（FK）、`store_id`。

**写时复制（Copy-on-Write）语义**：

- 店铺菜单查询 = `store_id = 0` 的共享品 + `store_id = 本店` 的专属品，同 `code` 时专属品覆盖共享品（`NOT EXISTS` 合并查询）；
- 商家编辑/下架共享品：先 `INSERT` 一份 `store_id = 本店` 的副本（保留 code/categoryId/价格等），再 `UPDATE` 副本——**共享记录永远只读**，各店初始数据一致、改后互不影响；
- 商家新增商品：直接生成 `store_id = 本店` 记录；同店同 `code` 新增返回 400；
- 无删除接口（商品只能下架，数据留痕）。

**凑单品（topup=1）**：种子数据 9 个共享低价品（珍珠小料杯 ¥3 / 椰果小料杯 ¥3 / 矿泉水 ¥3 / 芋圆小料杯 ¥4 / 苏打气泡水 ¥5 / 迷你曲奇 ¥4 / 迷你可颂 ¥5 / 迷你美式 ¥6 / 迷你柠檬茶 ¥6，均为 `store_id = 0`），商家也可编辑本店商品标记 topup=1（懒复制语义同普通编辑）。凑单推荐按「最低可买价 = MIN(price_small, price_medium, price_large, base_price)」过滤与升序。

### 2.2 menu_category — 分类表

| 列 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | 主键 |
| code | varchar(30) UNIQUE | 内置 5 个：`coffee`/`tea`/`dessert`/`food`/`ice`；自定义：`cus` + 毫秒时间戳（保证唯一） |
| name | varchar(30) | 分类名称 |
| icon | varchar(10) | 图标 emoji（缺省 🏷️） |
| sort_order | int | 排序（共享类目固定 0，自定义类目 0） |
| store_id | bigint | **归属**：`0` = 共享类目（所有店可见）/ `N` = 该商家创建的自定义类目（仅本店可见） |

查询语义：`WHERE store_id = 0 OR store_id = {本店}`，`ORDER BY (store_id = 0) DESC, sort_order, id`（共享在前）。

### 2.3 store / merchant / coffee_user

`store`：`id / code(唯一) / name / address / phone / business_hours / status(OPEN|CLOSED) / merchant_id(绑定商家，一商一店) / created_at / updated_at`。

`merchant`：`id / merchant_no(sj-xxx，唯一) / username / password(BCrypt) / nickname / phone / store_name / store_id / status(ACTIVE|DISABLED) / created_at / updated_at`。**占位商家机制**：每家门店预分配一条 `merchant_no` 已生成、资料为空、`status = DISABLED` 的记录；商家入驻 = 激活占位记录（填资料、置 ACTIVE、绑定店铺）。占位记录密码为空，一律拒绝登录。

`coffee_user`：`id / username(唯一) / password(BCrypt) / nickname / avatar_url / total_spent(累计消费) / member_level / points / points_level / last_store_id(上次选店偏好) / merchant_no(绑定商家，一账号一店) / role(USER|MERCHANT) / created_at / updated_at`。

### 2.4 seat / seat_template — 座位三表

`seat_template`（桌型模板，全店共用）：`id / seat_no(001-099 唯一) / capacity(2/4/8) / type_name / created_at`。001-070 双人桌、071-090 四人桌、091-099 多人桌。

`seat`（店铺座位实例）：`id / store_id / template_id / status(FREE|ASSIGNED|OCCUPIED) / assigned_user_id / assigned_guest_id / assigned_at / occupied_at / created_at / updated_at`，`(store_id, template_id)` 唯一索引。启动时 `SeatDataInitializer` 为营业店铺按模板自动补齐座位。

座位编号（对外）：`{店名}-{3位编号}`（如 `Fika・静安店-001`），二维码内容 = `{qr-base-url}/?seat=编号`。

### 2.5 user_order / order_item — 订单

`user_order`：`id / order_no(详细订单号，唯一索引 uk_order_no) / user_id / guest_id(二选一) / store_id(下单店铺) / fulfillment_type(PICKUP|DINE_IN|DELIVERY) / note / beverage_name(商品名快照) / size / condiments / original_price / final_price / voucher_no(核销的卡券包券码，可空) / status(UNPAID|PENDING|PREPARING|COMPLETED|CANCELED) / delivery_info_id / created_at / completed_at / started_at / estimated_ready_time / custom_size(定制规格如 300ml，仅 CUSTOM)`。用户与游客订单统一入此表，按店隔离（商家只能操作本店订单）。`DELIVERY` 仅允许登录用户并必须携带 `deliveryAddressId`，不会分配座位；地址快照与抢单状态落在 `delivery_order`。接口返回的 `orderType`（user/guest）为根据 `user_id`/`guest_id` 计算的展示字段，非数据库列。

### 2.9 delivery_address / delivery_rider / delivery_order — 外卖模块

- `delivery_address`：顾客可维护多条 `label / receiver_name / receiver_phone / detail_address`，可设置默认地址；地址按 `user_id` 隔离。
- `delivery_rider`：配送员独立账号，密码使用 BCrypt；当前支持 `ACTIVE / DISABLED`，登录后令牌身份为 `RIDER`。
- `delivery_order`：由 `DELIVERY` 主订单自动生成，创建时复制收货地址快照，状态为 `OPEN`。支付成功后才进入 C 端待抢列表；配送员通过数据库条件更新完成 `OPEN → CLAIMED → PICKED_UP → DELIVERING → DELIVERED`，抢单使用 CAS 保证同一订单只能被一人抢到。当前不计算配送费。

`order_no` 规则（2026-08-07）：`YYMMDD-{商家6位}-{类目3位}-{顺序3位}`，商家段 = `merchant_no` 去 `sj-` 前缀（无商家回退店铺 id），类目段 = 商品类目 id 左补 0（批量订单取首行商品类目），顺序段 = **店铺当日单号**（跨分类连续）。唯一索引保证并发下不重号，冲突由服务端重算重试。

`order_item`：`id / order_id(FK→user_order.id) / product_id(FK→menu_item.id) / product_name(名称快照，含规格前缀如"中杯 意式浓缩") / quantity(购物车行级数量，如 燕麦拿铁×2 = 2) / unit_price(折后单价 = subtotal÷quantity) / original_unit_price(单件原价，折前，明细行划线展示用；2026-08-07 新增，历史订单按订单折扣比例回填，尾差行可能差 1 分) / subtotal(折后小计)`。

**订单明细模型**：所有订单落库时同步写 `order_item` 明细（1 单 N 明细）——单品订单 1 条，批量订单每购物车行 1 条（`quantity` 保留行级数量）。会员折扣/优惠券按订单总额计算后按原价比例分摊到行（尾差归最后一行），明细小计合计 = 实付总额；批量订单 `beverage_name` 拼接「名称×数量」、`size` 记为 `MIXED`。已展开的历史批量订单（多条 `user_order`）保持不动。前端明细行展示「折后单价 + 划线原价」以体现减免（有折扣时划线，无折扣只显示单价）。

### 2.6 member_card / user_voucher — 会员体系

`member_card`：`id / user_id / card_no(唯一) / level(REGULAR|VIP|SVIP) / points / total_spent(累计消费快照) / exchange_points(累计已兑换) / status(1 正常|0 冻结) / created_at / updated_at`。开卡接口幂等，从 `coffee_user` 快照初始化。

`user_voucher`：`id / user_id / voucher_no(券码) / name / discount(面额) / minimum(门槛，0=无) / status(0 未使用|1 已使用|2 已过期) / source(REDEEM 兑换|GIFT 赠送) / created_at / expires_at`。下单会先校验归属、有效期及门槛，再以条件更新原子核销；待支付订单取消时按 `user_order.voucher_no` 自动返券。

### 2.7 after_sale / feedback — 售后模块

`after_sale`：`id / order_id(FK→user_order.id) / user_id(FK→coffee_user.id) / type(REFUND 退款|REMAKE 重做|EXCHANGE 换货|OTHER 其他) / reason(问题说明) / status(PENDING 待处理|PROCESSING 处理中|RESOLVED 已解决|REJECTED 已拒绝|CLOSED 已关闭) / handler_note(商家处理备注) / created_at / updated_at`。业务规则：仅已完成（COMPLETED）订单可申请、订单必须属于本人、同订单防重复（`uk_after_sale_user_order` 唯一约束）；商家只能按自己店铺查询并推进状态，完成/拒绝/关闭必须写处理说明。

`feedback`：`id / order_id / product_id(订单首个商品) / user_id / content(建议内容) / rating(TINYINT 1-5，可空) / created_at`。仅已完成订单可提交，同订单可多次反馈；商品评价查询不返回用户账号名。

### 2.8 payment — 支付单（2026-08-07 新建）

`payment`：`id / payment_no(VARCHAR(32)，唯一索引 uk_payment_no) / order_id(唯一索引 uk_payment_order，关联 user_order.id) / user_id(游客单为 null) / channel(WECHAT 微信|ALIPAY 支付宝|BANK 银行|MOCK 模拟，默认 MOCK) / amount(DECIMAL(10,2)，= 订单实付) / status(PENDING 待支付|PROCESSING 处理中|PAID 已支付|FAILED 支付失败|CLOSED 已关闭|REFUNDED 已退款，默认 PENDING) / transaction_no(渠道交易流水号) / channel_response(渠道原始响应) / paid_at(支付成功时间) / created_at / updated_at`。

业务规则：下单接口内部幂等创建（同订单仅一条有效支付单）；`MOCK` 渠道发起即成功；真实渠道（微信/支付宝/银行）为骨架占位（返回 501 未接入）；支付成功回写订单 `UNPAID → PENDING`，未支付订单不进入商家订单列表与今日统计。

### 2.9 request_idempotency — 下单请求幂等表（2026-08-09 新增）

`id / scope / idempotency_key / request_hash / status / response_body / created_at / updated_at`。`(scope, idempotency_key)` 唯一：scope 由下单身份组成（用户或游客），避免不同身份互相占用 key。首次请求插入 `PROCESSING`；成功后保存 `SUCCESS` 与完整订单响应；同 key 重试直接返回该响应。相同 key 对应不同请求指纹、或仍在处理时，接口返回 `409`，防止重复创建订单。

### 2.10 event_outbox — 可靠事件表（2026-08-09 新增）

`id / event_id / aggregate_type / aggregate_id / event_type / payload / status / attempts / last_error / published_at / created_at / updated_at`。订单创建和状态变更事件与订单数据在同一事务内写入；调度器通过 `PENDING → PUBLISHING` 条件更新抢占事件，只有抢占成功的实例可以投递，成功后置为 `PUBLISHED`，失败恢复为 `PENDING` 并累计 `attempts`。`PUBLISHING` 超过 5 分钟自动恢复，避免实例崩溃后永久卡死。当前 publisher 为本地日志实现，后续替换 RocketMQ producer 时不改变订单事务和补偿模型。

### 2.11 inventory_stock — 门店商品库存表（2026-08-09 新增）

`store_id + product_id` 唯一；`available_stock` 为可售库存，`locked_stock` 为待支付订单预扣数量，`version` 用于后续 Redis/Lua 与乐观锁扩展。首次售卖某门店商品初始化 100 份。创建订单以 `available_stock >= quantity` 为条件原子扣减，库存不足返回 409；取消待支付订单时按订单明细释放预扣库存。

### 2.12 其他运行时表

`user_location` 保存登录用户最近一次定位；`flash_sale_activity` / `flash_sale_claim` 保存秒杀活动与一次性资格；`agent_knowledge_document`、`agent_menu_embedding`、`agent_conversation`、`agent_conversation_message` 保存 Agent 的可追溯知识、向量缓存与身份隔离会话；`growth_agent_action` 保存商家 Agent 操作审计；`event_consume_log`、`user_notification` 分别用于消息消费幂等和站内通知。

## 三、核心设计模式

1. **身份双轨（user / guest）**：登录用户按 `user_id`、未登录按 `guest_id` 入库；下单、收藏、座位均支持双轨；游客登录后收藏经 `/api/favorites/merge` 合并。
2. **一商一店 + 占位商家**：商家入驻 = 激活门店占位记录；用户端账号与商家账号隔离，`coffee_user.merchant_no` 一账号仅可绑定一家店。
3. **写时复制商品**：见 2.1，保证多店共享数据一致性与商家自主编辑的平衡。
4. **共享 + 自定义类目**：见 2.2，类目按店可见性隔离。
5. **座位按店隔离**：分配/列表/占用均按 `store_id` 过滤，15 分钟未落座自动释放（`coffee.seat.assign-timeout-minutes` 可配）。

## 四、备份与迁移

- 备份产物：`sql_backup/`（mysqldump 结构备份，命名 `structure_backup_YYYYMMDD.sql`）。
- **新环境部署** = 建库 + 导入最新结构备份 + 手工导入共享数据 + 启动后端。种子店铺（21 家）与店铺座位由启动器（`StoreDataInitializer`/`SeatDataInitializer`）自动补齐；但**共享商品/共享类目无自动初始化器**——`store_id = 0` 的 50 个商品（41 个常规 + 9 个凑单品）与 5 个类目为存量数据，需从现有开发库导出（`SELECT ... WHERE store_id = 0` 的 `menu_item`/`menu_category` 行）或自行初始化，否则商家端菜单为空。
- 当前系统**无自动 DDL**（MyBatis-Plus 不做建表，Agent 服务也不在请求过程中建表），表结构变更需手工执行迁移并重新导出备份。请执行 `sql/migrations/V20260831_12_runtime_consistency.sql`；脚本使用 `information_schema` 动态 DDL，兼容 MySQL 5.7+/8.0+ 且可重复执行。唯一索引遇到历史重复数据时会安全失败，禁止脚本自动删除业务记录。
- 迁移前建议预检：`SELECT order_id, COUNT(*) FROM payment GROUP BY order_id HAVING COUNT(*) > 1`；`SELECT user_id, order_id, COUNT(*) FROM after_sale GROUP BY user_id, order_id HAVING COUNT(*) > 1`；`SELECT user_id, product_code, COUNT(*) FROM user_favorite WHERE user_id IS NOT NULL GROUP BY user_id, product_code HAVING COUNT(*) > 1`；游客收藏将 `user_id` 换为 `guest_id`；菜单、店铺、商家编号也应分别检查 `(store_id, code)`、`merchant_id`、`merchant_no` 重复。
- 历史重构记录：`product`/`product_category` → `menu_item`/`menu_category`（2026-08）；`guest_order` 并入 `user_order`；座位单表 → 三表（`seat_template`/`store`/`seat`）。
