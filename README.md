# FIKA 咖啡点单系统 - 后端

基于 Java 17 + Spring Boot 3.2.0 的咖啡店点单系统后端服务，DDD 多模块架构，提供 RESTful API。

## 技术栈

- Java 17 + Spring Boot 3.2.0
- MyBatis-Plus（持久层，DDD 分层：api / domain / infra）
- MySQL 8.0（数据库 `coffee_order_pro`）
- Maven 多模块（coffee-common + 各业务模块 coffee-module-*-biz + 聚合入口 coffee-web）
- ZXing 3.5.3（落座二维码生成）

## 项目结构

```
coffee-order-system-pro_back
├── coffee-common/              # 通用能力（Result 统一响应、异常、常量）
├── coffee-module-auth-biz/     # 认证模块：用户注册/登录/忘记密码/游客会话/店铺偏好
├── coffee-module-member-biz/   # 会员模块：会员中心数据（消费/等级/积分）
├── coffee-module-membership-biz# 会员体系模块：会员卡/等级规则/权益/积分兑换/卡券包
├── coffee-module-menu-biz/     # 菜单模块：商品/分类/收藏/图片存储（核心业务）
├── coffee-module-order-biz/    # 订单模块：下单/订单列表/状态流转/商家接单
├── coffee-module-seat-biz/     # 座位模块：取号/二维码/落座/离座/超时释放
├── coffee-module-store-biz/    # 店铺模块：店铺/商家（一商一店）/经营数据
├── coffee-web/                 # 聚合入口：Controller 层 + 全局异常 + 静态资源映射
├── docs/                       # 文档（API.md 接口权威文档 / DATABASE.md 数据库设计）
└── sql_backup/                 # 数据库结构备份（mysqldump 产物）
```

每个 `coffee-module-*-biz` 内部按 DDD 分层：

```
└── src/main/java/com/coffee/module/{module}/
    ├── api/            # 对外接口（Service 接口 + DTO）
    ├── domain/         # 领域层（领域模型 + 仓储接口 + 领域服务）
    └── infra/          # 基础设施（MyBatis-Plus 持久化实现 + 存储）
```

## 快速开始

### 1. 数据库准备

```sql
CREATE DATABASE coffee_order_pro DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

表结构以 `sql_backup/` 最新一份 mysqldump 为准，或直接以现有库为准。**当前无自动建表脚本**（MyBatis-Plus 不做 DDL），新环境需导入备份或手工建表。

### 2. 修改配置

编辑 `coffee-web/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/coffee_order_pro?useSSL=false&serverTimezone=Asia/Shanghai
    username: <你的用户名>
    password: <你的密码>

coffee:
  seat:
    qr-base-url: http://<本机局域网IP>:5173   # 二维码内容基础地址（手机扫码可达，生产改部署域名）
```

### 3. 启动后端

```bash
# 方式一：Maven 命令行（根 pom 聚合，启动 coffee-web）
mvn -pl coffee-web -am spring-boot:run

# 方式二：IDE 中运行 coffee-web 的 CoffeeWebApplication
```

服务启动在 `http://localhost:8088`，健康检查 `GET /health`。

### 4. 启动前端

配套前端：`../coffee-order-system-pro_front`（详见其 README，需在 vite 代理中同时配置 `/api` 与 `/uploads`）。

## 核心设计

- **写时复制（Copy-on-Write）商品**：`menu_item.store_id` 语义为归属店铺——`0` = 共享商品（所有店可见，仅一份），`N` = 该店专属副本。商家编辑/下架共享商品时自动复制一份 `store_id=N` 的副本再修改，共享记录永远只读，保证各店初始数据一致、修改互不影响。共享商品不可删除（无删除接口）。
- **共享类目 + 商家自定义类目**：`menu_category.store_id` 语义同上——`0` = 5 个内置共享类目（coffee/tea/dessert/food/ice），`N` = 该商家创建的自定义类目（仅本店可见）。自定义类目 `code` 为 `cus` + 毫秒时间戳，天然全局唯一。
- **店铺 + 商家双体系**：顾客账号（`coffee_user`）与商家账号（`merchant`）完全隔离；一商一店；21 家种子店铺启动时自动初始化；每家门店预分配占位商家记录，商家入驻 = 激活占位记录。
- **座位三表结构**：`seat_template`（99 桌型模板）+ `store` + `seat`（店铺座位实例），按店隔离，分配后 15 分钟未落座自动释放。
- **本地图片存储**：上传文件落盘 `{user.home}/coffee-uploads/{storeId}/{uuid}.{ext}`，后端静态映射 `/uploads/**` → 该目录，返回相对 URL（如 `/uploads/5/xxxx.png`）。**生产部署需在 nginx 配置 `/uploads` 静态代理**（开发环境由 vite 代理转发）。

## 文档索引

| 文档 | 说明 |
|---|---|
| `docs/API.md` | 接口权威文档（唯一，新增接口必须同步更新） |
| `docs/DATABASE.md` | 数据库设计文档（表结构总览 / 核心设计 / ER 关系） |

## 配置项一览

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `coffee.seat.qr-base-url` | http://localhost:5173 | 座位二维码内容基础地址（开发填局域网 IP，生产填域名） |
| `coffee.seat.assign-timeout-minutes` | 15 | 分配后未落座自动释放时长 |
| `coffee.seat.two-seats / four-seats / multi-seats` | 70 / 20 / 9 | 各桌型数量（每店） |

> 注：`application.yml` 中 `coffee.seat.store-name` 为历史遗留死配置（座位编号前缀实际取 `store.name`，见 docs/API.md 5.8），可清理。
