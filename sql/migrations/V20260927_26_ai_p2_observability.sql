-- P2: persist the versions needed to compare AI runs and evaluation results.
-- Execute after the existing Agent observability and evaluation migrations.

ALTER TABLE agent_run
    ADD COLUMN prompt_version VARCHAR(64) NOT NULL DEFAULT 'v1',
    ADD COLUMN model_version VARCHAR(64) NOT NULL DEFAULT 'unknown',
    ADD COLUMN knowledge_version VARCHAR(64) NOT NULL DEFAULT 'v1',
    ADD COLUMN tool_contract_version VARCHAR(64) NOT NULL DEFAULT 'v1';

ALTER TABLE agent_eval_result
    ADD COLUMN prompt_version VARCHAR(64) NOT NULL DEFAULT 'v1',
    ADD COLUMN model_version VARCHAR(64) NOT NULL DEFAULT 'unknown',
    ADD COLUMN knowledge_version VARCHAR(64) NOT NULL DEFAULT 'v1',
    ADD COLUMN tool_contract_version VARCHAR(64) NOT NULL DEFAULT 'v1';

CREATE INDEX idx_agent_run_store_started
    ON agent_run (store_id, started_at);

CREATE INDEX idx_agent_eval_version
    ON agent_eval_result (prompt_version, model_version, created_at);
