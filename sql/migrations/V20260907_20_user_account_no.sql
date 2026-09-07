-- FIKA 顾客系统账号号码。
-- 旧用户按现有自增 id 修复为 fika + 10 位数字；新用户由应用生成 fika + 10 位随机数字。
-- 例如：id=1 -> fika0000000001。
-- 依赖：coffee_user 表。
-- 本脚本可重复执行；不会修改 coffee_user.id，也不会影响订单等外键关系。

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'coffee_user' AND column_name = 'account_no') = 0,
    'ALTER TABLE coffee_user ADD COLUMN account_no VARCHAR(14) NULL COMMENT ''系统账号号码'' AFTER id',
    'SELECT 1'
);
PREPARE fika_account_no_stmt FROM @ddl;
EXECUTE fika_account_no_stmt;
DEALLOCATE PREPARE fika_account_no_stmt;

-- 只补齐没有账号号码的历史用户；重复执行不会覆盖已经生成的随机账号号码。
UPDATE coffee_user
SET account_no = CONCAT('fika', LPAD(id, 10, '0'))
WHERE account_no IS NULL OR TRIM(account_no) = '';

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'coffee_user'
        AND index_name = 'uk_coffee_user_account_no') = 0,
    'ALTER TABLE coffee_user ADD UNIQUE KEY uk_coffee_user_account_no (account_no)',
    'SELECT 1'
);
PREPARE fika_account_no_stmt FROM @ddl;
EXECUTE fika_account_no_stmt;
DEALLOCATE PREPARE fika_account_no_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'coffee_user'
        AND column_name = 'account_no' AND is_nullable = 'YES') = 1,
    'ALTER TABLE coffee_user MODIFY COLUMN account_no VARCHAR(14) NOT NULL COMMENT ''系统账号号码''',
    'SELECT 1'
);
PREPARE fika_account_no_stmt FROM @ddl;
EXECUTE fika_account_no_stmt;
DEALLOCATE PREPARE fika_account_no_stmt;
