package com.coffee.module.order.api.dto;

import lombok.Data;

/**
 * 凑单进度 DTO（购物袋进度条数据）
 */
@Data
public class TopupProgressDTO {
    /** 是否已满足全部满减门槛（购物袋金额 ≥ 所有候选门槛）：true 时无需凑单 */
    private Boolean reached;
    /** 目标门槛金额（最近一张未达成的满减券门槛；reached=true 时为最高门槛） */
    private Double threshold;
    /** 还差金额 = threshold - amount（reached=true 时为 0） */
    private Double gap;
    /** 目标券名（如"下午茶立减 ¥8"/卡券包券名；无候选时为空串） */
    private String couponName;
    /** 目标券编码（固定权益券 FIKA8/SWEET12/BEAN15；卡券包券/无候选时为空串） */
    private String couponCode;
    /** 目标券面额（reached=true 时无意义） */
    private Double discount;
}
