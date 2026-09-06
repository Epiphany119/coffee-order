-- FIKA Agent 运行观测与工具调用审计。
-- 运行前请先执行 V20260831_12_runtime_consistency.sql。

CREATE TABLE IF NOT EXISTS agent_run (
    run_id VARCHAR(40) NOT NULL,
    owner_key VARCHAR(80) NOT NULL,
    scene VARCHAR(40) NOT NULL,
    store_id BIGINT NULL,
    session_id VARCHAR(40) NULL,
    question TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    plan_json MEDIUMTEXT NULL,
    engine VARCHAR(100) NULL,
    answer TEXT NULL,
    error_message VARCHAR(500) NULL,
    tool_count INT NOT NULL DEFAULT 0,
    started_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    PRIMARY KEY (run_id),
    KEY idx_agent_run_owner (owner_key, scene, started_at),
    KEY idx_agent_run_store (store_id, started_at),
    KEY idx_agent_run_status (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 运行审计';

CREATE TABLE IF NOT EXISTS agent_tool_call (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(40) NOT NULL,
    sequence_no INT NOT NULL,
    tool_name VARCHAR(80) NOT NULL,
    read_only TINYINT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    result_count INT NOT NULL DEFAULT 0,
    arguments_json MEDIUMTEXT NULL,
    note VARCHAR(500) NULL,
    started_at DATETIME NOT NULL,
    finished_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_tool_sequence (run_id, sequence_no),
    KEY idx_agent_tool_name (tool_name, status, finished_at),
    CONSTRAINT fk_agent_tool_run FOREIGN KEY (run_id) REFERENCES agent_run(run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 工具调用审计';
