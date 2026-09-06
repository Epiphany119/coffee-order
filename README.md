# coffee-order

FIKA 咖啡点单系统统一代码仓库，后端位于项目根目录，前端位于 `frontend/`。

## 项目结构

- `coffee-web/`：后端 Web 启动模块
- `coffee-module-*-biz/`：后端业务模块
- `coffee-*-service/`、`coffee-gateway/`：可拆分服务与网关
- `frontend/`：Vue 3 + TypeScript + Vite 前端
- `sql/`：数据库初始化与迁移脚本
- `docs/`：接口、数据库和架构文档

Agent 设计、运行轨迹和评测入口见 [Agent 评测与面试演示](docs/AGENT_EVALUATION.md)。首次启用 Agent 审计时，额外执行 `sql/migrations/V20260906_17_agent_observability.sql`。

邮箱验证码登录、注册和用户中心绑定邮箱使用 QQ SMTP。执行 `sql/migrations/V20260906_18_email_auth.sql` 后，在启动环境设置 `QQ_MAIL_USERNAME` 和 `QQ_MAIL_PASSWORD`（QQ 邮箱授权码）；也支持同名的 `COFFEE_MAIL_USERNAME`、`COFFEE_MAIL_PASSWORD` 覆盖。验证码哈希会双写 Redis 与 MySQL：Redis 丢失时由 MySQL 兜底，验证码使用/过期后两边都会清理；默认每个邮箱 60 秒只能发送一次，10 分钟内第 6 次申请会进入 10 分钟冷却。需要本地启动 Redis 时可执行 `docker compose -f docker-compose.redis.yml up -d`，Redis 暂不可用时验证码仍可依靠 MySQL 工作。用户中心只有通过一次成功的绑定验证码校验才会写入邮箱，已绑定邮箱可直接解绑。

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
