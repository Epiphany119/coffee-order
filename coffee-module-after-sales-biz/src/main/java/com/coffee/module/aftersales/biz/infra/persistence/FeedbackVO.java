package com.coffee.module.aftersales.biz.infra.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单反馈查询 VO（含订单信息）
 */
@Data
public class FeedbackVO extends FeedbackPO {

    /** 关联订单详细订单号 */
    private String orderNo;

    /** 关联订单商品名快照 */
    private String beverageName;

    /** 反馈用户名（来自 coffee_user.username，唯一） */
    private String username;
}
