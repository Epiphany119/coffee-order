# FIKA Agent 评测与运行审计

## 产品 Agent 边界

项目只有两个面向用户的 Agent：

- **顾客 Agent**：`CustomerSupervisorAgentOrchestrator` 统一分流咨询/推荐、点单、订单查询和反馈入口。既有点单计划服务属于顾客 Agent 的受控业务步骤；生成计划后必须由顾客确认，服务端再校验并幂等创建订单、模拟支付。
- **店长增长 Agent**：读取所属门店经营数据和授权知识，生成服务端保存的 DRAFT 提案；店长确认后，后端按白名单通过 Outbox 执行。

`AgentOperationsController` 只提供运行轨迹、评测和汇总接口，不提供聊天，不是第三个 Agent。评测不会通过任意工具或模型输出直接写订单、支付、营销或库存。

## 运行轨迹

执行基础 Agent 观测迁移 `V20260906_17_agent_observability.sql`、评测迁移 `V20260908_24_agent_evaluation_observability.sql` 及项目要求的后续 P1/P2 迁移后，可查看：

- 结构化路由、运行状态、模型/规则来源和失败原因；
- 工具步骤、只读标记、耗时、返回条数和降级说明；
- 字符估算 Token、成本来源和模型版本；
- 顾客点单链的订单结果；商家运行的门店范围与提案确认状态。

运行轨迹通过 `GET /api/agent-operations/runs/{runId}` 查询，服务端只返回当前身份自己的记录。用户侧统一入口为 `POST /api/customer-agent/assistant`；专用点单入口为 `/api/customer-agent/plan` 与 `/api/customer-agent/plans/confirm`。

## 评测接口

所有接口前缀为 `/api/agent-operations`：

- `GET /evaluations/cases`：读取固定评测样例；
- `POST /evaluations/run`：执行单条样例，请求体示例 `{ "caseId": "customer-safety-prompt-injection", "storeId": 5 }`；
- `POST /evaluations/run-all`：按当前身份执行对应的 customer 或 merchant 样例；
- `GET /evaluations/summary`：读取当前身份的通过数、路由/动作断言、拒绝断言和平均耗时。

顾客样例调用真实顾客 Supervisor；店长样例调用真实店长分析，但以 `prepareAction=false` 运行，不持久化提案，也不执行动作。评测记录位于 `agent_eval_result`。断言字段为 `expectedRoute`、`expectedActionType`、`forbiddenActions`、`requiresConfirmation` 和 `expectedOutcome`。这组评测不覆盖真实订单支付、Outbox 投递或跨服务集成结果。

门店归属评测要求以商户身份请求不属于该商户的 `storeId`；顾客与店长的 scene 与身份不匹配时应由权限层拒绝。

## 知识库隔离

- 顾客知识检索只返回 `CUSTOMER_PUBLIC` 文档，并限制为全局或当前门店文档。
- 店长知识检索仅返回当前所属门店的 `CUSTOMER_PUBLIC` 与 `MERCHANT_INTERNAL` 文档。
- 向量召回后的 MySQL 回查和关键词检索都执行相同范围校验；Milvus 返回的 ID 本身不作为授权依据。
- 手工知识默认 `MERCHANT_INTERNAL`。菜单知识同步为 `CUSTOMER_PUBLIC`。

新的可见性字段迁移为 `sql/migrations/V20261001_27_ai_agent_consolidation.sql`。确认 P1 `V20260926_25_ai_p1_completion.sql`、P2 `V20260927_26_ai_p2_observability.sql` 已执行并备份后，由项目负责人手工执行；应用不会自动执行该文件。

## 建议验证次序

1. 先运行 `AgentSceneAuthorizationTest` 与 `AgentEvaluationMatcherTest`。
2. 验证顾客公开知识可检索、内部知识不可检索，以及门店范围隔离。
3. 验证店长提案的归属、版本、哈希、过期、重复确认和 Outbox 重试。
4. 验证顾客 plan/confirm、重复确认和模拟支付回调幂等。
5. 通过集成环境验证经营数据异常返回失败而不是零值，并核验两端评测 API 权限。

从项目根目录运行后端测试：`mvn -pl coffee-web -am test`。本轮只更新测试文件，不运行单元或集成测试；不要在测试结束前对外表述为生产级交付或质量验证通过。

## 面试演示

1. 顾客在 `/api/customer-agent/assistant` 咨询，再走点单计划、确认、订单和模拟支付链路；展示未确认不建单。
2. 店长查看本店经营信号与知识来源，生成 DRAFT 提案；展示 actionId、版本和哈希。
3. 确认时只传 actionId/版本；执行时服务端读取已保存正文并进入 Outbox。
4. 以另一门店身份尝试读取或执行该提案，展示归属拒绝。
5. 用 `/api/agent-operations/runs/{runId}` 查看运行记录，并说明它是运维观测接口，不是产品 Agent。

模型准确率、延迟或成本须以实际评测和运行数据为准，不填写未经测量的数字。