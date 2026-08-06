package com.coffee.module.membership.biz.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户卡券领域实体（卡券包）
 */
@Data
public class UserVoucher {

    /** 状态 */
    public static final int STATUS_UNUSED = 0;
    public static final int STATUS_USED = 1;
    public static final int STATUS_EXPIRED = 2;

    /** 来源 */
    public static final String SOURCE_REDEEM = "REDEEM";

    private Long id;
    private Long userId;
    private String voucherNo;
    private String name;
    private Double discount;
    private Double minimum;
    private Integer status;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
