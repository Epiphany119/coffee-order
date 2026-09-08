-- FIKA 站内通知关联订单
--
-- 订单状态通知点击后需要打开完整订单详情，因此为通知增加可选 order_id。
-- 非订单通知（营销、抢购资格等）继续保持 NULL。
-- 适用：MySQL 5.7+/8.0+；请先 USE coffee_order_pro，再执行本脚本。

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'user_notification' AND column_name = 'order_id') = 0,
    'ALTER TABLE user_notification ADD COLUMN order_id BIGINT NULL COMMENT ''关联的用户订单主键'' AFTER user_id',
    'SELECT 1'
);
PREPARE fika_notification_order_link_stmt FROM @ddl;
EXECUTE fika_notification_order_link_stmt;
DEALLOCATE PREPARE fika_notification_order_link_stmt;

-- 兼容已经产生的配送/完成通知：旧版本把订单号写在标题的 # 后面。
UPDATE user_notification
SET order_id = CAST(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(title, '#', -1), ' ', 1)) AS UNSIGNED)
WHERE order_id IS NULL
  AND type IN ('DELIVERY_DELIVERED', 'ORDER_COMPLETED')
  AND title LIKE '%#%';
