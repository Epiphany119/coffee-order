-- MANUAL DATABASE MIGRATION
-- Back up the database and verify V20260926_25 and V20260927_26 were applied first.
-- Execute this file manually. It is not run automatically by the application or this coding task.
-- Existing knowledge defaults to merchant-only. Only known menu bootstrap sources are made public.
-- Existing PENDING actions without confirmation provenance are canceled.

ALTER TABLE agent_knowledge_document
    ADD COLUMN visibility VARCHAR(24) NOT NULL DEFAULT 'MERCHANT_INTERNAL';

CREATE INDEX idx_agent_knowledge_visibility_store
    ON agent_knowledge_document (visibility, store_id, enabled);

UPDATE agent_knowledge_document
SET visibility = 'CUSTOMER_PUBLIC'
WHERE source IN (
    'menu-catalog-bootstrap',
    'menu-semantic-bootstrap',
    'menu-planning-bootstrap',
    'menu-exclusion-bootstrap'
);

ALTER TABLE growth_agent_action
    ADD COLUMN confirmed_at DATETIME NULL,
    ADD COLUMN expires_at DATETIME NULL;

CREATE INDEX idx_growth_agent_action_expiry
    ON growth_agent_action (status, expires_at);

UPDATE growth_agent_action
SET status = 'CANCELED'
WHERE status = 'PENDING' AND confirmed_at IS NULL;
