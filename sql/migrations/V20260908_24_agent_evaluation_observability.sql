-- 扩展 Agent 运行观测：降级原因、模型用量/成本和最终下单结果。
-- 先执行 V20260906_17_agent_observability.sql；本迁移也兼容旧版 agent_run 表。

ALTER TABLE agent_run
    ADD COLUMN fallback_reason VARCHAR(255) NULL AFTER engine,
    ADD COLUMN model_name VARCHAR(100) NULL AFTER tool_count,
    ADD COLUMN model_call_count INT NOT NULL DEFAULT 0 AFTER model_name,
    ADD COLUMN input_tokens INT NOT NULL DEFAULT 0 AFTER model_call_count,
    ADD COLUMN output_tokens INT NOT NULL DEFAULT 0 AFTER input_tokens,
    ADD COLUMN total_tokens INT NOT NULL DEFAULT 0 AFTER output_tokens,
    ADD COLUMN model_cost DECIMAL(18,8) NULL AFTER total_tokens,
    ADD COLUMN model_cost_currency VARCHAR(8) NULL DEFAULT 'CNY' AFTER model_cost,
    ADD COLUMN model_cost_source VARCHAR(32) NULL AFTER model_cost_currency,
    ADD COLUMN order_success TINYINT NULL AFTER model_cost_source,
    ADD COLUMN order_id BIGINT NULL AFTER order_success;

CREATE TABLE IF NOT EXISTS agent_eval_result (
    id BIGINT NOT NULL AUTO_INCREMENT,
    case_id VARCHAR(80) NOT NULL,
    run_id VARCHAR(40) NULL,
    owner_key VARCHAR(80) NOT NULL,
    tool_selection_correct TINYINT NOT NULL DEFAULT 0,
    forbidden_tool_avoided TINYINT NOT NULL DEFAULT 0,
    confirmation_correct TINYINT NOT NULL DEFAULT 0,
    outcome_correct TINYINT NOT NULL DEFAULT 0,
    passed TINYINT NOT NULL DEFAULT 0,
    expected_json MEDIUMTEXT NOT NULL,
    actual_json MEDIUMTEXT NULL,
    error_message VARCHAR(1000) NULL,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_agent_eval_owner (owner_key, created_at),
    KEY idx_agent_eval_case (case_id, created_at),
    KEY idx_agent_eval_passed (passed, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 评测结果';
