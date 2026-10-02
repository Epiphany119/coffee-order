-- Creates the durable order-request idempotency table and guarantees the key constraint.
-- Before applying to an existing database, inspect the duplicate-key result below.
-- No existing rows are deleted or rewritten by this migration.

CREATE TABLE IF NOT EXISTS request_idempotency (
    id BIGINT NOT NULL AUTO_INCREMENT,
    scope VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_body MEDIUMTEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_idempotency_scope_key (scope, idempotency_key),
    KEY idx_request_idempotency_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- If the table predates this migration, review every returned row before continuing.
SELECT scope, idempotency_key, COUNT(*) AS duplicate_count
FROM request_idempotency
GROUP BY scope, idempotency_key
HAVING COUNT(*) > 1;

-- Add the unique index to an existing table only when an equivalent unique index is absent.
-- ALTER TABLE intentionally fails when duplicate keys remain; this script never auto-deduplicates them.
SET @has_request_idempotency_unique_key = (
    SELECT COUNT(*)
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'request_idempotency'
          AND non_unique = 0
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') = 'scope,idempotency_key'
    ) existing_indexes
);

SET @request_idempotency_ddl = IF(
    @has_request_idempotency_unique_key = 0,
    'ALTER TABLE request_idempotency ADD UNIQUE KEY uk_request_idempotency_scope_key (scope, idempotency_key)',
    'SELECT ''request_idempotency unique key already exists'' AS migration_status'
);
PREPARE request_idempotency_stmt FROM @request_idempotency_ddl;
EXECUTE request_idempotency_stmt;
DEALLOCATE PREPARE request_idempotency_stmt;
