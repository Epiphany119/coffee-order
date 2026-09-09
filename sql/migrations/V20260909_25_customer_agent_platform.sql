-- 顾客侧 Supervisor Agent 的会话、知识、菜单向量与偏好记忆。
-- 这些表都是 Agent 的可选基础设施；业务查询失败时应用会安全降级，不影响普通点单。

CREATE TABLE IF NOT EXISTS agent_knowledge_document (
    id BIGINT NOT NULL AUTO_INCREMENT,
    store_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    source VARCHAR(120) NOT NULL DEFAULT 'manual',
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_knowledge_scope_title (store_id, title),
    KEY idx_agent_knowledge_enabled_scope (enabled, store_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 知识库原文';

CREATE TABLE IF NOT EXISTS agent_menu_embedding (
    id BIGINT NOT NULL AUTO_INCREMENT,
    store_id BIGINT NOT NULL,
    product_code VARCHAR(100) NOT NULL,
    text_hash VARCHAR(128) NOT NULL,
    vector_json MEDIUMTEXT NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_menu_embedding_product (store_id, product_code),
    KEY idx_agent_menu_embedding_store (store_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 菜单语义向量缓存';

CREATE TABLE IF NOT EXISTS agent_conversation (
    session_id VARCHAR(40) NOT NULL,
    owner_key VARCHAR(80) NOT NULL,
    scene VARCHAR(40) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (session_id),
    KEY idx_agent_conversation_owner (owner_key, scene, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 身份隔离会话';

CREATE TABLE IF NOT EXISTS agent_conversation_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(40) NOT NULL,
    owner_key VARCHAR(80) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_agent_conversation_message_session (session_id, owner_key, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 会话消息';

CREATE TABLE IF NOT EXISTS agent_customer_preference (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_key VARCHAR(80) NOT NULL,
    store_id BIGINT NOT NULL,
    preference_json MEDIUMTEXT NOT NULL,
    last_message_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_customer_preference_owner_store (owner_key, store_id),
    KEY idx_agent_customer_preference_updated (store_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='顾客 Agent 偏好记忆';
