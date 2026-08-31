package com.coffee.module.order.api.dto;

import lombok.Data;

/**
 * 订单简要信息（跨模块只读查询用，如售后模块校验订单归属）
 */
@Data
public class OrderBrief {

    private Long id;

    /** 详细订单号 */
    private String orderNo;

    /** 商品名快照 */
    private String beverageName;

    /** 状态：UNPAID/PENDING/ACCEPTED/PREPARING/READY_FOR_DELIVERY/RIDER_ASSIGNED/DELIVERING/DELIVERED/COMPLETED/CANCELED */
    private String status;

    /** 实付金额（支付模块建支付单用） */
    private Double finalPrice;

    /** 下单用户 id（游客单为 null） */
    private Long userId;

    /** 游客订单归属（登录用户订单为 null） */
    private String guestId;

    /** 下单店铺 */
    private Long storeId;

    /** 订单第一个明细的商品 id（反馈归属规则：反馈记录挂在第一个品下） */
    private Long firstProductId;
}
