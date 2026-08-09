package com.coffee.module.aftersales.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后单响应
 */
@Data
public class AfterSaleResponse {

    private Long id;

    /** 关联订单 id（取餐号） */
    private Long orderId;

    /** 订单详细订单号 */
    private String orderNo;

    /** 订单商品名快照 */
    private String orderName;

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
