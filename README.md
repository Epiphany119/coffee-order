# coffee-order

FIKA 咖啡点单系统统一代码仓库，后端位于项目根目录，前端位于 `frontend/`。

## 项目结构

- `coffee-web/`：后端 Web 启动模块
- `coffee-module-*-biz/`：后端业务模块
- `coffee-*-service/`、`coffee-gateway/`：可拆分服务与网关
- `frontend/`：Vue 3 + TypeScript + Vite 前端
- `sql/`：数据库初始化与迁移脚本
- `docs/`：接口、数据库和架构文档

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
