package com.coffee.module.membership.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 积分兑换项 DTO（接口下发，前端免硬编码）
 */
@Data
public class RedeemItemDTO {
    private String code;
    /** 兑换项展示名，如 "12 元无门槛券 ×5 + 10 元无门槛券 ×4" */
    private String name;
    /** 所需积分 */
    private Integer costPoints;
    /** 拆分明细：兑换后按张发放 */
    private List<Grant> grants;

    @Data
    public static class Grant {
        private String name;
        private Double discount;
        private Integer count;
    }
}
