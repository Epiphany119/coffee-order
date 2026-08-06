package com.coffee.module.membership.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 积分兑换结果 DTO
 */
@Data
public class RedeemResultDTO {
    private String itemCode;
    private String itemName;
    /** 本次消耗积分 */
    private Integer costPoints;
    /** 兑换后剩余积分 */
    private Integer remainingPoints;
    /** 本次发放到卡券包的券列表 */
    private List<VoucherDTO> vouchers;
    private String message;
}
