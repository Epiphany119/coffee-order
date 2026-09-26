-- P1 AI business completion baseline. Run once on the application database.
-- MySQL is the source of truth; Redis is used for coordination and retry queues.

ALTER TABLE growth_agent_action
    ADD COLUMN analysis_id VARCHAR(64) NULL,
    ADD COLUMN proposal_version INT NOT NULL DEFAULT 1,
    ADD COLUMN proposal_hash CHAR(64) NULL,
    ADD COLUMN queued_at DATETIME NULL;

CREATE INDEX idx_growth_agent_action_analysis ON growth_agent_action (analysis_id);

CREATE TABLE growth_agent_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    action_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at DATETIME NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    executed_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_growth_outbox_target (action_id, target_user_id, operation_type),
    KEY idx_growth_outbox_dispatch (status, next_attempt_at),
    CONSTRAINT fk_growth_outbox_action FOREIGN KEY (action_id) REFERENCES growth_agent_action(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE growth_agent_delivery (
    outbox_id BIGINT NOT NULL,
    action_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    executed_at DATETIME NULL,
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_growth_delivery_target (action_id, target_user_id, operation_type),
    CONSTRAINT fk_growth_delivery_outbox FOREIGN KEY (outbox_id) REFERENCES growth_agent_outbox(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE payment
    ADD UNIQUE KEY uk_payment_transaction_no (transaction_no);

ALTER TABLE agent_knowledge_document
    ADD COLUMN document_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN content_hash CHAR(64) NULL,
    ADD COLUMN embedding_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN embedding_updated_at DATETIME NULL,
    ADD COLUMN embedding_error VARCHAR(500) NULL;

CREATE INDEX idx_agent_knowledge_embedding_status
    ON agent_knowledge_document (embedding_status, updated_at);
