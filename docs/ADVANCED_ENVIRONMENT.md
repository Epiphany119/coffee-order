# FIKA 中间件演示环境

这套环境把项目已经实现的 Redis、RocketMQ、Nacos、Gateway 和请求链路观测能力真正跑起来，适合本地联调或面试演示。它不会启动业务服务，也不会改动已有 MySQL 数据库。

## 1. 启动中间件

在后端根目录执行：

```bash
docker compose -f docker-compose.middleware.yml up -d
```

启动后可打开：

| 组件 | 地址 | 用途 |
|---|---|---|
| Nacos | `http://localhost:8848/nacos` | 服务注册与配置中心 |
| RocketMQ Dashboard | `http://localhost:8180` | 查看 `fika-order-events` 主题、生产/消费情况 |
| Jaeger | `http://localhost:16686` | 查询 OTLP Trace |
| Redis | `localhost:6379` | 菜单/库存快照与秒杀 Lua 库存 |

Nacos 以 standalone 模式启动且演示环境未开启鉴权，仅限本机使用。停止并清理容器可执行：

```bash
docker compose -f docker-compose.middleware.yml down
```

## 2. 启动顺序

1. 确认 MySQL 已按项目迁移脚本升级；
2. 启动上述 Compose；
3. 使用 `prod` 配置启动 `coffee-web`，并通过环境变量提供数据库、Redis、RocketMQ、Nacos 和令牌密钥；
4. 可选启动 `coffee-gateway`，用户请求改访问 `http://localhost:8090/api/**`；
5. 在用户端完成下单、支付、商家完成订单：RocketMQ Dashboard 中可查看订单事件，Jaeger 中可按服务名查看 Trace。

示例（变量只用于本机临时演示，真实环境应放在密钥管理系统）：

```bash
export COFFEE_DB_URL='jdbc:mysql://127.0.0.1:3306/coffee_order_pro?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai'

# 智谱 GLM（店长增长 Agent 与顾客点单 Agent 的自然语言回复）
# 请在本机 shell、IDE Run Configuration 或部署平台的密钥管理中设置，切勿提交到 Git。
export ZHIPU_API_KEY='你的智谱 API Key'
# 可选：默认 glm-4.5-flash
export ZHIPU_MODEL='glm-4.5-flash'
export COFFEE_DB_USERNAME='你的数据库账号'
export COFFEE_DB_PASSWORD='你的数据库密码'
export COFFEE_REDIS_HOST='127.0.0.1'
export COFFEE_ROCKETMQ_NAMESRV='127.0.0.1:9876'
export NACOS_SERVER_ADDR='127.0.0.1:8848'
export COFFEE_AUTH_TOKEN_SECRET='至少32字符的随机生产密钥'
export COFFEE_PAYMENT_CALLBACK_SECRET='至少32字符的随机回调密钥'
export COFFEE_OTLP_TRACING_ENDPOINT='http://127.0.0.1:4318/v1/traces'
mvn -pl coffee-web -am spring-boot:run -Dspring-boot.run.profiles=prod,rocketmq
```

## 3. 当前真实边界

- 已可演示：Redis 菜单/库存快照、订单幂等、Outbox、RocketMQ 订单完成事件与积分/通知幂等消费、Nacos 注册、Gateway 路由、Sentinel 网关规则、Request-Id 与 OTLP 导出、Redis Lua 秒杀。
- 独立的 `coffee-order-service`、`coffee-inventory-service`、`coffee-marketing-service` 当前是可单独注册/启动的服务边界和内部接口，不是已经切流完成的生产微服务；用户端默认仍走 `coffee-web`。
- Seata 尚未接入。当前跨领域一致性采用本地事务 + Outbox + 可靠消息 + 幂等消费/补偿；不要在简历或答辩中描述为已使用 Seata。

这样陈述更符合工程事实：已落地的能力可以现场运行和验证，未完成的微服务切流与 Seata 是明确的下一步，而不是“配置文件里有依赖就算完成”。
