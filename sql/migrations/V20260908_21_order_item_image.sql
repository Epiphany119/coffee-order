-- FIKA 订单明细商品图片快照
--
-- 用途：订单详情展示每个商品的缩略图；新订单保存下单时图片，历史订单按商品 id 回填。
-- 适用：MySQL 5.7+/8.0+；请先 USE coffee_order_pro，再执行本脚本。

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'order_item' AND column_name = 'image_url') = 0,
    'ALTER TABLE order_item ADD COLUMN image_url VARCHAR(500) NULL COMMENT ''下单时商品图片快照'' AFTER product_name',
    'SELECT 1'
);
PREPARE fika_order_item_image_stmt FROM @ddl;
EXECUTE fika_order_item_image_stmt;
DEALLOCATE PREPARE fika_order_item_image_stmt;

-- 仅回填空值，已保存的历史快照不会被覆盖。
UPDATE order_item oi
JOIN menu_item mi ON mi.id = oi.product_id
SET oi.image_url = mi.image_url
WHERE (oi.image_url IS NULL OR oi.image_url = '');
