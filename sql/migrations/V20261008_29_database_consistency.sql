-- Forward-only schema alignment based on the coffee_order_pro MySQL 8.0 schema review.
-- Requires the V25-V28 tables and columns to exist. No rows are deleted or rewritten.
-- Re-runnable: column changes only widen types, and index DDL is guarded by metadata checks.

SELECT DATABASE() AS active_database, VERSION() AS mysql_version;

-- Keep the deployed scope width and allow complete order-response JSON to exceed TEXT's 64 KiB limit.
ALTER TABLE request_idempotency
    MODIFY COLUMN scope VARCHAR(160) NOT NULL,
    MODIFY COLUMN response_body MEDIUMTEXT NULL;

-- created_at is the canonical lookup axis for eventual idempotency-record retention.
SET @request_idempotency_has_created_index = (
    SELECT COUNT(*)
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'request_idempotency'
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') = 'created_at'
    ) AS matching_indexes
);

SET @request_idempotency_created_index_ddl = IF(
    @request_idempotency_has_created_index = 0,
    'ALTER TABLE request_idempotency ADD KEY idx_request_idempotency_created_at (created_at)',
    'SELECT ''request_idempotency created_at index already exists'' AS migration_status'
);
PREPARE request_idempotency_created_index_stmt FROM @request_idempotency_created_index_ddl;
EXECUTE request_idempotency_created_index_stmt;
DEALLOCATE PREPARE request_idempotency_created_index_stmt;

-- V28 created an updated_at index on a fresh table. Normalize it to the deployed created_at index.
SET @request_idempotency_has_updated_index = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'request_idempotency'
      AND index_name = 'idx_request_idempotency_updated_at'
);

SET @request_idempotency_updated_index_ddl = IF(
    @request_idempotency_has_updated_index > 0,
    'ALTER TABLE request_idempotency DROP INDEX idx_request_idempotency_updated_at',
    'SELECT ''request_idempotency updated_at index is absent'' AS migration_status'
);
PREPARE request_idempotency_updated_index_stmt FROM @request_idempotency_updated_index_ddl;
EXECUTE request_idempotency_updated_index_stmt;
DEALLOCATE PREPARE request_idempotency_updated_index_stmt;

-- Keep one (store_id, started_at) index. Drop the V26 index only when an equivalent index remains.
SET @agent_run_store_started_index_count = (
    SELECT COUNT(*)
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'agent_run'
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') = 'store_id,started_at'
    ) AS matching_indexes
);

SET @agent_run_has_v26_store_index = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_run'
      AND index_name = 'idx_agent_run_store_started'
);

SET @agent_run_duplicate_index_ddl = IF(
    @agent_run_has_v26_store_index > 0 AND @agent_run_store_started_index_count > 1,
    'ALTER TABLE agent_run DROP INDEX idx_agent_run_store_started',
    'SELECT ''agent_run store index is absent or has no equivalent duplicate'' AS migration_status'
);
PREPARE agent_run_duplicate_index_stmt FROM @agent_run_duplicate_index_ddl;
EXECUTE agent_run_duplicate_index_stmt;
DEALLOCATE PREPARE agent_run_duplicate_index_stmt;

-- Post-migration verification.
SELECT table_name, column_name, column_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'request_idempotency'
  AND column_name IN ('scope', 'response_body');

SELECT table_name, index_name, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND (
      table_name = 'request_idempotency'
      OR (table_name = 'agent_run' AND index_name IN ('idx_agent_run_store', 'idx_agent_run_store_started'))
  )
ORDER BY table_name, index_name, seq_in_index;
