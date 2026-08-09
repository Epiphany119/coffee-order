package com.coffee.module.aftersales.api.dto;

import lombok.Data;

/**
 * 创建售后单请求
 */
@Data
public class AfterSaleRequest {

    /** 售后用户 id */
    private Long userId;

    /** 关联订单 id */
    private Long orderId;

    /** 售后类型：REFUND 退款 / REMAKE 重做 / EXCHANGE 换货 / OTHER 其他 */
    private String type;

    /** 问题说明 */
    private String reason;
}
