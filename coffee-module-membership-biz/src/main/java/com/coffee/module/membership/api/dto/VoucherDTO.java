package com.coffee.module.membership.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户卡券 DTO（卡券包里的券）
 */
@Data
public class VoucherDTO {
    private Long id;
    /** 券码 */
    private String voucherNo;
    /** 券名 */
    private String name;
    /** 面额 */
    private Double discount;
    /** 使用门槛，0 = 无门槛 */
    private Double minimum;
    /** 状态：0 未使用 / 1 已使用 / 2 已过期 */
    private Integer status;
    /** 来源：REDEEM 积分兑换 / GIFT 赠送 */
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
