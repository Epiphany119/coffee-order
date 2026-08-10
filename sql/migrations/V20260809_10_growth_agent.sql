-- FIKA 店长增长 Agent：审批操作与审计记录（MySQL 5.7+/8.0+）
-- 执行前请先备份；脚本使用 information_schema 保证可重复执行。

CREATE TABLE IF NOT EXISTS growth_agent_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL COMMENT 'NOTIFY_MEMBER/CREATE_VOUCHER/RESTOCK_ALERT',
    title VARCHAR(128) NOT NULL,
    proposal_json TEXT NOT NULL COMMENT 'Agent 生成、待商家确认的方案快照',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/EXECUTED/CANCELED/FAILED',
    executed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_growth_agent_action_store_status (store_id, status, created_at),
    KEY idx_growth_agent_action_merchant (merchant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店长增长Agent操作审计';
