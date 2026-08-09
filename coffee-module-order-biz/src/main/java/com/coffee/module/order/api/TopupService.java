package com.coffee.module.order.api;

import com.coffee.module.order.api.dto.TopupProgressDTO;

/**
 * 凑单服务（购物袋满减进度条）
 *
 * 门槛规则：用户已选券 → 按该券门槛计算；未选券 → 卡券包未使用满减券（status=0 且 minimum>0）优先，
 * 其次固定权益券 FIKA8(满48减8)/SWEET12(满78减12)/BEAN15(满88减15)，取 "门槛 > 购物袋金额" 的最小门槛。
 * 游客无卡券包，只看固定权益券。全部门槛已满足时 reached=true（前端展示"已达标"）。
 */
public interface TopupService {

    /**
     * 计算凑单进度（购物袋金额距最近满减门槛还差多少）
     *
     * @param userId     登录用户 id（游客传 null，仅看固定权益券）
     * @param amount     购物袋金额（会员折后、券前，与下单优惠券可用性判断口径一致）
     * @param couponCode 已选优惠券编码（可空；固定权益券 FIKA8/SWEET12/BEAN15）
     */
    TopupProgressDTO getTopupProgress(Long userId, Double amount, String couponCode);
}
