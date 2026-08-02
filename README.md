# FIKA 咖啡点单系统 - 后端

基于 Spring Boot 的咖啡店点单系统后端服务，提供 RESTful API。

## 技术栈

- Java 17 + Spring Boot 3.2.0
- Spring Data JPA + MySQL
- 设计模式：装饰器、观察者、状态、策略、单例、工厂

## 项目结构

```
src/main/java/com/coffee/order/
├── controller/        # REST API 控制器
│   ├── AuthController.java   # 登录/注册
│   └── OrderController.java  # 菜单/订单/会员
├── service/          # 业务逻辑
├── entity/           # JPA 实体
├── dto/              # 数据传输对象
├── repository/       # 数据访问层
├── config/           # 配置类
├── decorator/        # 装饰器模式（饮品配料）
├── observer/         # 观察者模式（订单通知）
├── state/            # 状态模式（订单状态）
├── strategy/         # 策略模式（会员定价）
├── factory/          # 工厂模式（饮品制造）
├── model/            # 模型类
└── singleton/        # 单例模式
```

## 快速开始

### 1. 数据库准备

```sql
CREATE DATABASE coffee_order_pro;
-- 使用项目根目录的 init.sql 初始化数据
```

### 2. 修改配置

编辑 `src/main/resources/application.properties`：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/coffee_order_pro?useSSL=false&serverTimezone=Asia/Shanghai
spring.datasource.username=你的用户名
spring.datasource.password=你的密码
```

### 3. 启动后端

```bash
mvn spring-boot:run
# 或在 IDE 中直接运行 CoffeeOrderSystemApplication
```

服务启动在 `http://localhost:8088`

## API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/auth/login` | POST | 用户登录 |
| `/api/auth/register` | POST | 用户注册 |
| `/api/auth/user/{id}` | GET | 获取用户信息 |
| `/api/menu` | GET | 获取菜单 |
| `/api/order` | POST | 创建订单 |
| `/api/orders/user/{id}` | GET | 用户订单列表 |
| `/api/orders/guest/{id}` | GET | 游客订单列表 |
| `/api/order/user/{id}/action` | POST | 更新用户订单状态 |
| `/api/order/guest/{id}/action` | POST | 更新游客订单状态 |
| `/api/member/{id}/dashboard` | GET | 会员中心数据 |

## 前端配套

配套前端项目：`../coffee-order-system-pro_front`

启动前端后会自动代理 `/api` 请求到本服务。
