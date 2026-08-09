package com.coffee.module.aftersales.biz.infra.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后单查询 VO（含订单信息）
 */
@Data
public class AfterSaleVO extends AfterSalePO {

    /** 关联订单详细订单号 */
    private String orderNo;

    /** 关联订单商品名快照 */
    private String beverageName;
}
