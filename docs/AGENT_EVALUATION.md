# FIKA Agent 评测与面试演示

## 项目定位

FIKA Agent 采用 `结构化计划 → 白名单只读工具 → 有来源证据 → 受控回答` 的链路。
模型不直接连接数据库，也不能执行下单、扣款、发券或改库存。需要改变业务状态时，必须进入已有的“方案预览 → 人工确认 → 幂等执行 → 审计”链路。

## 一次运行的可观察信息

执行 `V20260906_17_agent_observability.sql`、`V20260908_24_agent_evaluation_observability.sql` 和 `V20260909_25_customer_agent_platform.sql` 后，每次 `/api/business-agent/ask`、`/stream`、顾客点单 Agent 的 `plan/confirm` 以及顾客侧统一 Supervisor 都会生成 `runId`，并记录：

顾客侧统一入口为 `POST /api/customer-agent/assistant`；它会把咨询/推荐、点单、订单查询和反馈/售后请求关联到会话与同一套运行观测记录。

- 结构化意图、工具步骤、模型或规则路由来源；
- 每个工具的只读标记、执行状态、耗时、返回条数和降级说明；
- 模型调用次数、字符估算 token、成本单价/币种/估算来源；
- 最终运行状态、失败原因，以及 `orderSuccess` / `orderId`（顾客点单链路）。

顾客侧 Supervisor 还会把咨询、点单、订单查询、反馈/售后路由与会话 ID 关联起来；偏好记忆只保存经过规则提取的有限字段，不保存无限增长的原始 Prompt。

使用 `GET /api/business-agent/runs/{runId}` 可读取当前身份自己的工具调用轨迹。

## 评测执行接口

评测集位于 `eval/agent_cases.jsonl`，运行时从 `coffee-web/src/main/resources/agent/agent_cases.jsonl` 加载。服务端固定读取样例文本，不接受浏览器改写 Prompt；所有样例只调用 Business Agent 的只读工具，不会创建订单、扣款或修改库存。

- `GET /api/business-agent/evaluations/cases`：查看当前版本评测集；
- `POST /api/business-agent/evaluations/run`：执行一条样例，请求体 `{ "caseId": "customer-safety-prompt-injection", "storeId": 5 }`；
- `POST /api/business-agent/evaluations/run-all`：按当前身份执行全部 customer 或 merchant 样例，并逐条记录工具断言；
- `GET /api/business-agent/evaluations/summary`：读取当前身份已执行的通过数、工具选择通过数、越权工具拦截数、确认断言通过数和平均耗时。

结果落在 `agent_eval_result`，至少包含：允许工具选择是否正确、`mustNotCall` 是否全部避开、是否按要求要求确认、是否按预期拒绝以及最终是否通过。`expectedOutcome=REJECTED` 的门店越权样例，需要用不属于当前商家的 `storeId` 执行，才能验证权限拒绝。

## 建议评测集

评测样例位于 `eval/agent_cases.jsonl`。当前集合覆盖门店错误、商品缺货、价格错误、预算超限、重复确认、越权查询和 Prompt Injection。每条样例至少包含：

- `scene`：`customer` 或 `merchant`；
- `input`：用户问题；
- `expectedTools`：允许出现的工具子集；
- `mustNotCall`：绝不能调用的工具；
- `requiresConfirmation`：是否应提示人工确认；
- `expectedOutcome`：期望正常回答 `ANSWERED`，或被权限层拒绝 `REJECTED`。

建议在模型、Prompt、召回策略发生变化后，重新执行全部样例并记录：

1. 工具选择准确率；
2. 门店权限隔离通过率；
3. RAG 命中与来源引用率；
4. 工具执行成功率和降级率；
5. P95 延迟与单次模型成本；
6. 重复确认造成的重复执行数，应为 0；订单确认仍由 `Idempotency-Key` 持久化幂等链路兜底。

模型成本说明：当前 `ZhipuChatClient` 记录的是请求/响应字符估算 token，并在轨迹中以 `modelCostSource=estimated_chars` 标注；只有配置 `COFFEE_AI_INPUT_COST_PER_1K`、`COFFEE_AI_OUTPUT_COST_PER_1K` 后才会计算有价格含义的成本，未配置时显示 `estimated_chars_unpriced`，不要把它当成供应商账单。

## 面试演示脚本

1. 店长提问：`最近复购下降，但今天待制作订单很多，应该怎么做？`
2. 展示 `structuredPlan`：先读知识、订单、履约和经营指标，只读工具执行；
3. 展示工具调用的 `callId`、耗时、返回条数和来源；
4. Agent 给出小范围、低风险方案，并明确“未执行”；
5. 店长确认后进入营销动作接口，重复点击仍由幂等键和状态机保护；
6. 使用 `runId` 重新打开运行轨迹，展示完整审计记录。

不要在简历中填写未经评测集测量的准确率或延迟数字。
