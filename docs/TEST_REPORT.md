# coffee-order 测试收尾报告

**测试日期**：2026-09-27

**项目版本**：`ebf3b2c`（工作区包含本报告和新增测试）

**目标**：完成当前代码阶段的自动化回归、前端构建检查和企业级 AI 核心边界测试，区分代码验证结果与必须依赖外部环境的发布门禁。

## 1. 测试结论

后端 Maven 全量测试和前端生产构建均通过。新增测试覆盖了 P0/P1/P2 中之前缺少证据的 HMAC 请求鉴权、Nonce 防重放、AI 限流、门店指标计算和数据库不可用提示。

数据库迁移、真实基础设施冒烟、第三方模型/支付联调、压测和浏览器端 E2E 尚未执行，因此本报告支持“代码和自动化测试通过”，不等同于生产发布通过。

## 2. 执行环境

| 项目 | 实际环境 |
|---|---|
| Java | Microsoft OpenJDK 21.0.12；Maven 编译目标为项目配置的 Java 17 |
| Maven | 3.9.16 |
| Node.js / npm | v24.18.0 / 11.16.0 |
| 后端 | Spring Boot 多模块 Maven Reactor |
| 前端 | Vue 3 + TypeScript + Vite |

## 3. 自动化测试结果

### 3.1 后端全量回归

执行命令：

```bash
mvn -B test
```

结果：

- 22 个 Maven 模块参与 Reactor 构建；
- 18 个 Surefire 测试报告文件；
- **57 个测试通过**；
- failures：0；errors：0；skipped：0；
- Maven Reactor：`BUILD SUCCESS`。

关键覆盖范围：

| 范围 | 覆盖内容 |
|---|---|
| 认证与密码 | 密码修改、原密码校验、Token 行为 |
| 订单与支付 | 订单领域规则、支付状态、订单幂等 |
| 库存与事件 | 库存业务、Outbox 投递失败后的重试状态 |
| 顾客 Agent | 意图解析、规则降级、计划注册、工具白名单 |
| Agent 评测 | 计划解析、评测匹配、请求追踪 |
| 内部服务鉴权 | HMAC 签名、请求体摘要、Nonce 重放和篡改拒绝 |
| AI 可靠性 | 限流配额、HTTP 429、`Retry-After`、身份隔离 |
| P2 指标 | 窗口天数边界、成功率/转化率/来源覆盖率、未迁移时 503 |

本轮新增测试文件：

- `coffee-web/src/test/java/com/coffee/common/internal/InternalRequestSignerTest.java`
- `coffee-web/src/test/java/com/coffee/web/security/AiRateLimitInterceptorTest.java`
- `coffee-web/src/test/java/com/coffee/web/agent/AgentBusinessMetricsServiceTest.java`
- `coffee-inventory-service/src/test/java/com/coffee/inventory/security/InternalAuthenticationFilterTest.java`

为内部鉴权测试补充了 `coffee-inventory-service` 的 test scope `spring-boot-starter-test` 依赖；不影响生产运行时依赖。

### 3.2 前端生产构建

执行目录：`frontend/`

执行命令：

```bash
npm run build
```

结果：`vue-tsc` 类型检查和 `vite build` 均通过，构建产物写入 `frontend/dist/`。

构建日志中的非阻断提示：

- Dart Sass legacy API 即将废弃；
- Rollup 提示部分 chunk 大于 500 kB；
- `@vueuse/core` 个别 `/* #__PURE__ */` 注释位置被 Rollup 忽略。

这些提示没有导致构建失败，但应在性能优化和依赖升级时处理。

### 3.3 代码卫生

```bash
git diff --check
```

通过，未发现已修改文件的空白错误。

## 4. 数据库和外部依赖验证

本次没有执行 SQL 迁移。原因是本机没有可用的 MySQL 服务，`mysqladmin ping` 无法连接，Docker daemon 也未运行。当前仓库没有可直接用于 coffee-order 的完整临时基线数据库，因此不能安全地把 `V20260926_25_ai_p1_completion.sql` 和 `V20260927_26_ai_p2_observability.sql` 当作已执行。

以下项目仍需在一次性测试库或 CI 环境完成：

1. 按 Agent 基础迁移 → P1 → P2 的顺序执行 SQL，并检查表、字段、索引和旧数据兼容性；
2. 登录 → 顾客咨询 → 计划确认 → 模拟支付 → 履约查询；
3. 商家分析 → 提案确认 → Outbox 重试 → 券/通知幂等；
4. 知识库写入、Embedding/Milvus 失败后的重试；
5. Redis、RocketMQ、Nacos、GLM、SMTP 和真实支付适配器联调；
6. 备份、回滚、Redis 故障和 Outbox 恢复演练。

当前自动化测试使用内存对象、Mock/JDBC stub 或本地规则，未产生真实订单、支付或第三方调用副作用。

## 5. 尚未覆盖的质量门禁

- 没有配置 JaCoCo，因此本报告不提供代码覆盖率百分比；
- 没有浏览器端单元测试或 Playwright/Cypress E2E；
- 没有并发、P95、限流容量、队列积压和故障注入数据；
- 未在真实 MySQL/Redis/Milvus/RocketMQ/Nacos 环境启动应用做 HTTP 冒烟；
- 生产环境必须使用 Redis Replay Store 和真实密钥，不能把内存回放存储或模拟支付视为生产能力。

## 6. 发布判断

| 门禁 | 状态 | 证据 |
|---|---|---|
| Java 编译与后端单元回归 | 已通过 | `mvn -B test`，57/57 |
| HMAC、重放、限流、指标核心边界 | 已通过 | 本轮新增 10 个测试 |
| 前端类型检查与生产构建 | 已通过 | `npm run build` |
| 数据库迁移 | 待目标环境执行 | 本机无可用 MySQL/Docker |
| 真实环境业务冒烟 | 待执行 | 需要数据库、Redis、模型和密钥 |
| 性能、恢复、E2E 和覆盖率门禁 | 待执行 | 尚未配置对应基础设施 |

当前版本可以作为**自动化测试通过的交付候选版本**继续做环境验收；完成上表待执行项后，再标记为生产发布通过。
