package com.coffee.module.aftersales.biz.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后单领域对象
 */
@Data
public class AfterSale {

    private Long id;

    /** 关联订单 id（user_order.id） */
    private Long orderId;

    /** 售后用户（coffee_user.id） */
    private Long userId;

    /** 售后类型：REFUND 退款 / REMAKE 重做 / EXCHANGE 换货 / OTHER 其他 */
    private String type;

    /** 问题说明 */
    private String reason;

    /** 状态：PENDING 待处理 / PROCESSING 处理中 / RESOLVED 已解决 / REJECTED 已拒绝 / CLOSED 已关闭 */
    private String status;

    /** 商家处理备注 */
    private String handlerNote;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
