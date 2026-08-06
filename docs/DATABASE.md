# FIKA 咖啡点单系统 — 数据库设计

> 数据库：`coffee_order_pro`（MySQL 8.0，utf8mb4 / utf8mb4_unicode_ci）。本文档描述当前线上结构（2026-08-06），结构变更后请同步更新本文档并重新导出 `sql_backup/` 备份。

## 一、表总览（22 张）

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
| 订单 | `delivery_info` | 配送信息（预留） |
| 订单 | `review` | 评价（预留） |
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

索引：`category_id`（FK）、`store_id`。

**写时复制（Copy-on-Write）语义**：

- 店铺菜单查询 = `store_id = 0` 的共享品 + `store_id = 本店` 的专属品，同 `code` 时专属品覆盖共享品（`NOT EXISTS` 合并查询）；
- 商家编辑/下架共享品：先 `INSERT` 一份 `store_id = 本店` 的副本（保留 code/categoryId/价格等），再 `UPDATE` 副本——**共享记录永远只读**，各店初始数据一致、改后互不影响；
- 商家新增商品：直接生成 `store_id = 本店` 记录；同店同 `code` 新增返回 400；
- 无删除接口（商品只能下架，数据留痕）。

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

`user_order`：`id / user_id / guest_id(二选一) / store_id(下单店铺) / fulfillment_type(PICKUP|DINE_IN) / note / beverage_name(商品名快照) / size / condiments / original_price / final_price / status(PENDING|PREPARING|COMPLETED|CANCELED) / delivery_info_id / created_at / completed_at / started_at / estimated_ready_time / custom_size(定制规格如 300ml，仅 CUSTOM)`。用户与游客订单统一入此表，按店隔离（商家只能操作本店订单）。接口返回的 `orderType`（user/guest）为根据 `user_id`/`guest_id` 计算的展示字段，非数据库列。

`order_item`：`id / order_id / product_id / product_name / quantity / unit_price / subtotal`。

### 2.6 member_card / user_voucher — 会员体系

`member_card`：`id / user_id / card_no(唯一) / level(REGULAR|VIP|SVIP) / points / total_spent(累计消费快照) / exchange_points(累计已兑换) / status(1 正常|0 冻结) / created_at / updated_at`。开卡接口幂等，从 `coffee_user` 快照初始化。

`user_voucher`：`id / user_id / voucher_no(券码) / name / discount(面额) / minimum(门槛，0=无) / status(0 未使用|1 已使用|2 已过期) / source(REDEEM 兑换|GIFT 赠送) / created_at / expires_at`。

## 三、核心设计模式

1. **身份双轨（user / guest）**：登录用户按 `user_id`、未登录按 `guest_id` 入库；下单、收藏、座位均支持双轨；游客登录后收藏经 `/api/favorites/merge` 合并。
2. **一商一店 + 占位商家**：商家入驻 = 激活门店占位记录；用户端账号与商家账号隔离，`coffee_user.merchant_no` 一账号仅可绑定一家店。
3. **写时复制商品**：见 2.1，保证多店共享数据一致性与商家自主编辑的平衡。
4. **共享 + 自定义类目**：见 2.2，类目按店可见性隔离。
5. **座位按店隔离**：分配/列表/占用均按 `store_id` 过滤，15 分钟未落座自动释放（`coffee.seat.assign-timeout-minutes` 可配）。

## 四、备份与迁移

- 备份产物：`sql_backup/`（mysqldump 结构备份，命名 `structure_backup_YYYYMMDD.sql`）。
- **新环境部署** = 建库 + 导入最新结构备份 + 手工导入共享数据 + 启动后端。种子店铺（21 家）与店铺座位由启动器（`StoreDataInitializer`/`SeatDataInitializer`）自动补齐；但**共享商品/共享类目无自动初始化器**——`store_id = 0` 的 41 个商品与 5 个类目为存量数据，需从现有开发库导出（`SELECT ... WHERE store_id = 0` 的 `menu_item`/`menu_category` 行）或自行初始化，否则商家端菜单为空。
- 当前系统**无自动 DDL**（MyBatis-Plus 不做建表），表结构变更需手工 DDL 并重新导出备份。
- 历史重构记录：`product`/`product_category` → `menu_item`/`menu_category`（2026-08）；`guest_order` 并入 `user_order`；座位单表 → 三表（`seat_template`/`store`/`seat`）。
