-- FIKA runtime consistency migration
--
-- 目标：补齐代码当前实际使用的运行时表，并把应用层幂等升级为数据库约束。
-- 适用：MySQL 5.7+/8.0+；请在目标库中执行，不要在未选择数据库时执行。
-- 本脚本不删除数据、不重建核心业务表。唯一索引若因历史重复数据失败，
-- 应先按 docs/DATABASE.md 的预检 SQL 清理重复数据，再重新执行失败段。

CREATE TABLE IF NOT EXISTS request_idempotency (
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- 两个字段只接受应用层 ASCII key；显式使用 ascii 让 MySQL 5.7
    -- 未开启 innodb_large_prefix 时也能创建完整唯一索引。
    scope VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    response_body MEDIUMTEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_idempotency_scope_key (scope, idempotency_key),
    KEY idx_request_idempotency_status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口幂等请求记录';

CREATE TABLE IF NOT EXISTS event_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload MEDIUMTEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL,
    published_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_outbox_event_id (event_id),
    KEY idx_event_outbox_dispatch (status, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务消息 Outbox';

CREATE TABLE IF NOT EXISTS event_consume_log (
    event_id VARCHAR(64) NOT NULL,
    consumer VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, consumer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息消费幂等记录';

CREATE TABLE IF NOT EXISTS user_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    read_status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_notification_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户站内通知';

CREATE TABLE IF NOT EXISTS inventory_stock (
    store_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    available_stock INT NOT NULL DEFAULT 0,
    locked_stock INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (store_id, product_id),
    KEY idx_inventory_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店商品库存';

CREATE TABLE IF NOT EXISTS user_location (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_location_user (user_id),
    KEY idx_user_location_updated (updated_at),
    CONSTRAINT fk_user_location_user FOREIGN KEY (user_id) REFERENCES coffee_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录用户最近一次定位';

CREATE TABLE IF NOT EXISTS flash_sale_activity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    store_id BIGINT NOT NULL,
    product_code VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    flash_price DECIMAL(10,2) NOT NULL,
    available_stock INT NOT NULL DEFAULT 0,
    sold_stock INT NOT NULL DEFAULT 0,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_flash_sale_current (store_id, enabled, start_at, end_at),
    KEY idx_flash_sale_product (store_id, product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='限时秒杀活动';

CREATE TABLE IF NOT EXISTS flash_sale_claim (
    id BIGINT NOT NULL AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    identity_key VARCHAR(120) NOT NULL,
    claim_no VARCHAR(40) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CLAIMED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flash_sale_identity (activity_id, identity_key),
    UNIQUE KEY uk_flash_sale_claim_no (claim_no),
    KEY idx_flash_sale_claim_status (status, created_at),
    KEY idx_flash_sale_claim_identity (identity_key, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀资格';

CREATE TABLE IF NOT EXISTS growth_agent_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    proposal_json MEDIUMTEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    executed_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_growth_agent_scope (merchant_id, store_id, id),
    KEY idx_growth_agent_status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店长 Agent 操作审计';

-- Agent 原文知识表在早期迁移中已有定义；这里保留 IF NOT EXISTS，
-- 让只执行本次运行时迁移的环境也不会因 Agent 接口缺表而失败。
CREATE TABLE IF NOT EXISTS agent_knowledge_document (
    id BIGINT NOT NULL AUTO_INCREMENT,
    store_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    source VARCHAR(80) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_knowledge (store_id, title),
    KEY idx_agent_knowledge_scope (enabled, store_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent RAG 知识库原始文档';

CREATE TABLE IF NOT EXISTS agent_menu_embedding (
    store_id BIGINT NOT NULL,
    product_code VARCHAR(80) NOT NULL,
    text_hash VARCHAR(100) NOT NULL,
    vector_json MEDIUMTEXT NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (store_id, product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单语义向量缓存';

CREATE TABLE IF NOT EXISTS agent_conversation (
    session_id VARCHAR(40) NOT NULL,
    owner_key VARCHAR(80) NOT NULL,
    scene VARCHAR(40) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id),
    KEY idx_agent_conversation_owner (owner_key, scene, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 会话';

CREATE TABLE IF NOT EXISTS agent_conversation_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(40) NOT NULL,
    owner_key VARCHAR(80) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_agent_message_session (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 会话消息';

-- 兼容较早的 user_order 结构：当前代码会读写券码。
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'user_order' AND column_name = 'voucher_no') = 0,
    'ALTER TABLE user_order ADD COLUMN voucher_no VARCHAR(64) NULL COMMENT ''核销的卡券包券码''',
    'SELECT 1'
);
PREPARE fika_stmt FROM @ddl;
EXECUTE fika_stmt;
DEALLOCATE PREPARE fika_stmt;

-- 反馈商品归属列在部分早期库中不存在。
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'feedback' AND column_name = 'product_id') = 0,
    'ALTER TABLE feedback ADD COLUMN product_id BIGINT NULL COMMENT ''反馈归属商品 id''',
    'SELECT 1'
);
PREPARE fika_stmt FROM @ddl;
EXECUTE fika_stmt;
DEALLOCATE PREPARE fika_stmt;

-- 以下唯一约束把“先查询再插入”的应用幂等升级为数据库级并发安全。
-- 若旧库存在重复行，ALTER TABLE 会拒绝执行并保留数据，禁止迁移自动删除业务记录。
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'payment' AND index_name = 'uk_payment_order') = 0,
    'ALTER TABLE payment ADD UNIQUE KEY uk_payment_order (order_id)',
    'SELECT 1'
);
PREPARE fika_stmt FROM @ddl;
EXECUTE fika_stmt;
DEALLOCATE PREPARE fika_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'after_sale' AND index_name = 'uk_after_sale_user_order') = 0,
    'ALTER TABLE after_sale ADD UNIQUE KEY uk_after_sale_user_order (user_id, order_id)',
    'SELECT 1'
);
PREPARE fika_stmt FROM @ddl;
EXECUTE fika_stmt;
DEALLOCATE PREPARE fika_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'user_favorite' AND index_name = 'uk_favorite_user_product') = 0,
    'ALTER TABLE user_favorite ADD UNIQUE KEY uk_favorite_user_product (user_id, product_code)',
    'SELECT 1'
);
PREPARE fika_stmt FROM @ddl;
EXECUTE fika_stmt;
DEALLOCATE PREPARE fika_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'user_favorite' AND index_name = 'uk_favorite_guest_product') = 0,
    'ALTER TABLE user_favorite ADD UNIQUE KEY uk_favorite_guest_product (guest_id, product_code)',
    'SELECT 1'
);
PREPARE fika_stmt FROM @ddl;
EXECUTE fika_stmt;
DEALLOCATE PREPARE fika_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'menu_item' AND index_name = 'uk_menu_item_store_code') = 0,
    'ALTER TABLE menu_item ADD UNIQUE KEY uk_menu_item_store_code (store_id, code)',
    'SELECT 1'
);
PREPARE fika_stmt FROM @ddl;
EXECUTE fika_stmt;
DEALLOCATE PREPARE fika_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'store' AND index_name = 'uk_store_merchant') = 0,
    'ALTER TABLE store ADD UNIQUE KEY uk_store_merchant (merchant_id)',
    'SELECT 1'
);
PREPARE fika_stmt FROM @ddl;
EXECUTE fika_stmt;
DEALLOCATE PREPARE fika_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'merchant' AND index_name = 'uk_merchant_no') = 0,
    'ALTER TABLE merchant ADD UNIQUE KEY uk_merchant_no (merchant_no)',
    'SELECT 1'
);
PREPARE fika_stmt FROM @ddl;
EXECUTE fika_stmt;
DEALLOCATE PREPARE fika_stmt;
