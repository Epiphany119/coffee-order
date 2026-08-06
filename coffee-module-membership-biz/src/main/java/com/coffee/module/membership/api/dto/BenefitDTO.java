package com.coffee.module.membership.api.dto;

import lombok.Data;

/**
 * 会员权益 DTO（等级折扣 + 权益券）
 */
@Data
public class BenefitDTO {
    private String code;
    private String name;
    /** 折扣权益：discountRate；券权益：minimum/discount */
    private String type;
    private Double discountRate;
    private Double minimum;
    private Double discount;
    private String description;
}
