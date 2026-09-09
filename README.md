# coffee-order

FIKA 咖啡点单系统统一代码仓库，后端位于项目根目录，前端位于 `frontend/`。

## 项目结构

- `coffee-web/`：后端 Web 启动模块
- `coffee-module-*-biz/`：后端业务模块
- `coffee-*-service/`、`coffee-gateway/`：可拆分服务与网关
- `frontend/`：Vue 3 + TypeScript + Vite 前端
- `sql/`：数据库初始化与迁移脚本
- `docs/`：接口、数据库和架构文档

Agent 设计、运行轨迹和评测入口见 [Agent 评测与面试演示](docs/AGENT_EVALUATION.md)。首次启用 Agent 审计时，额外执行 `sql/migrations/V20260906_17_agent_observability.sql`、`sql/migrations/V20260908_24_agent_evaluation_observability.sql` 和 `sql/migrations/V20260909_25_customer_agent_platform.sql`。用户端统一入口为 `POST /api/customer-agent/assistant`，由 Supervisor 分发咨询/推荐、点单、订单查询和反馈子 Agent；下单与售后仍必须经过用户确认。

邮箱验证码登录、注册和用户中心绑定邮箱使用 QQ SMTP。执行 `sql/migrations/V20260906_18_email_auth.sql`、`sql/migrations/V20260907_19_multi_email_binding.sql` 和 `sql/migrations/V20260907_20_user_account_no.sql` 后，在启动环境设置 `QQ_MAIL_USERNAME` 和 `QQ_MAIL_PASSWORD`（QQ 邮箱授权码）；也支持同名的 `COFFEE_MAIL_USERNAME`、`COFFEE_MAIL_PASSWORD` 覆盖。验证码哈希会双写 Redis 与 MySQL：Redis 丢失时由 MySQL 兜底，验证码使用/过期后两边都会清理；默认每个邮箱 60 秒只能发送一次，10 分钟内第 6 次申请会进入 10 分钟冷却。需要本地启动 Redis 时可执行 `docker compose -f docker-compose.redis.yml up -d`，Redis 暂不可用时验证码仍可依靠 MySQL 工作。邮箱注册需要填写唯一用户名；系统会另外生成 `fika` + 10 位随机数字的账号号码，进入主界面后可在个人资料查看。旧用户由 V20 按原有 id 修复为 `fika` + 10 位数字，例如 id=1 为 `fika0000000001`。登录账号输入框支持用户名、账号号码和已绑定邮箱。一个用户最多绑定 3 个邮箱，一个邮箱只能绑定一个用户；邮箱输入框失焦时自动检查占用状态，邮箱注册和绑定接口也会在后端再次校验。用户中心按邮箱列表提供单个解绑入口，前端确认后才执行解绑。

## 本地运行

启动后端：

```bash
mvn -pl coffee-web -am spring-boot:run -Dspring-boot.run.profiles=local
```

启动前端：

```bash
cd frontend
npm install
npm run dev
```

默认情况下，前端开发服务通过 Vite 将 `/api` 和 `/uploads` 请求代理到本地后端。

## Git 历史

本仓库保留了原后端与原前端的两条完整提交历史，并通过一个合并提交统一。后续前后端改动可在同一分支、同一次提交中协同维护。
