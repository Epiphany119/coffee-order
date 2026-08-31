-- FIKA 外卖订单链路修正
--
-- 订单主表状态：
-- UNPAID -> PENDING -> ACCEPTED -> PREPARING -> READY_FOR_DELIVERY
--          -> RIDER_ASSIGNED -> DELIVERING -> DELIVERED
-- 到店自取/店内用餐则 PREPARING -> COMPLETED。
-- 配送任务只有 OPEN 状态才会出现在骑手待抢列表。
-- 适用：MySQL 5.7+/8.0+；请在 V20260831_13_delivery_module.sql 后执行。

-- 先按已经存在的配送任务修正外卖主订单，保留已抢、已取餐、配送中和已送达进度。
UPDATE user_order o
JOIN delivery_order d ON d.order_id = o.id
SET o.status = CASE
    WHEN d.status IN ('CLAIMED', 'PICKED_UP') THEN 'RIDER_ASSIGNED'
    WHEN d.status = 'DELIVERING' THEN 'DELIVERING'
    WHEN d.status = 'DELIVERED' THEN 'DELIVERED'
    WHEN d.status = 'CANCELED' THEN 'CANCELED'
    WHEN d.status IN ('WAITING_MERCHANT', 'OPEN') AND o.status = 'COMPLETED' THEN 'READY_FOR_DELIVERY'
    ELSE o.status
END
WHERE o.fulfillment_type = 'DELIVERY';

-- 再让配送任务状态与主订单状态对齐：旧版本在支付后就 OPEN，必须收回到等待商家制作。
UPDATE delivery_order d
JOIN user_order o ON o.id = d.order_id
SET d.status = CASE
    WHEN o.status IN ('UNPAID', 'PENDING', 'ACCEPTED', 'PREPARING') THEN 'WAITING_MERCHANT'
    WHEN o.status = 'READY_FOR_DELIVERY' THEN 'OPEN'
    WHEN o.status = 'RIDER_ASSIGNED' THEN
        CASE WHEN d.status IN ('PICKED_UP', 'DELIVERING') THEN d.status ELSE 'CLAIMED' END
    WHEN o.status = 'DELIVERING' THEN 'DELIVERING'
    WHEN o.status = 'DELIVERED' THEN 'DELIVERED'
    WHEN o.status = 'CANCELED' THEN 'CANCELED'
    ELSE d.status
END
WHERE o.fulfillment_type = 'DELIVERY';

ALTER TABLE delivery_order
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'WAITING_MERCHANT'
    COMMENT 'WAITING_MERCHANT/OPEN/CLAIMED/PICKED_UP/DELIVERING/DELIVERED/CANCELED';
