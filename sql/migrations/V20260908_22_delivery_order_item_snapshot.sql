-- FIKA 外卖配送单商品图片快照
--
-- 配送单不能只依赖 item_summary：菜单商品后续可能被修改/下架，骑手和商家仍需看到下单时的商品图片。
-- item_details 保存 DeliveryOrderItem JSON 快照；本脚本同时回填已有配送单。
-- 适用：MySQL 5.7+/8.0+；请先 USE coffee_order_pro，再执行本脚本。

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'delivery_order' AND column_name = 'item_details') = 0,
    'ALTER TABLE delivery_order ADD COLUMN item_details TEXT NULL COMMENT ''配送商品名称、数量和图片 JSON 快照'' AFTER item_summary',
    'SELECT 1'
);
PREPARE fika_delivery_item_snapshot_stmt FROM @ddl;
EXECUTE fika_delivery_item_snapshot_stmt;
DEALLOCATE PREPARE fika_delivery_item_snapshot_stmt;

-- 只回填空快照，已经保存的下单图片不会被菜单当前图片覆盖。
UPDATE delivery_order d
JOIN (
    SELECT oi.order_id,
           JSON_ARRAYAGG(JSON_OBJECT(
               'beverageName', oi.product_name,
               'imageUrl', oi.image_url,
               'quantity', oi.quantity,
               'unitPrice', oi.unit_price,
               'originalUnitPrice', oi.original_unit_price,
               'subtotal', oi.subtotal
           )) AS item_details
    FROM order_item oi
    GROUP BY oi.order_id
) snapshot ON snapshot.order_id = d.order_id
SET d.item_details = snapshot.item_details
WHERE d.item_details IS NULL OR d.item_details = '';
