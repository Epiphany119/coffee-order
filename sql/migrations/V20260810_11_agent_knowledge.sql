-- RAG 知识库原文：MySQL 仅保存业务文本和门店权限；向量由应用同步到 Milvus。
CREATE TABLE IF NOT EXISTS agent_knowledge_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id BIGINT NULL COMMENT 'NULL 为全局知识，非空时仅该门店可检索',
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    source VARCHAR(80) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_agent_knowledge (store_id, title),
    KEY idx_agent_knowledge_scope (enabled, store_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent RAG 知识库原始文档';
