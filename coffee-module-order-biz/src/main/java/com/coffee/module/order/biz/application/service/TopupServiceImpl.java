package com.coffee.module.order.biz.application.service;

import com.coffee.module.membership.api.MembershipService;
import com.coffee.module.membership.api.dto.VoucherDTO;
import com.coffee.module.order.api.TopupService;
import com.coffee.module.order.api.dto.TopupProgressDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 凑单服务实现（购物袋满减进度条）
 *
 * 门槛规则（与下单优惠券口径一致）：
 * - 固定权益券：FIKA8 满48减8 / SWEET12 满78减12 / BEAN15 满88减15
 * - 卡券包：status=0（未使用）且 minimum>0（满减券，无门槛券不参与凑单）
 * - 已选券（couponCode）→ 只按该券门槛计算；未选券 → 卡券包优先 + 固定权益券，取最近门槛
 * - 游客（userId=null）：无卡券包，只看固定权益券
 */
@Service
public class TopupServiceImpl implements TopupService {

    /** 固定权益券（与下单优惠券 code 一一对应，门槛/面额与 OrderApplicationService.applyCoupon 保持一致） */
    private static final String[][] FIXED_COUPONS = {
            {"FIKA8", "下午茶立减 ¥8", "48", "8"},
            {"SWEET12", "甜品满 ¥78 减 ¥12", "78", "12"},
            {"BEAN15", "咖啡满 ¥88 减 ¥15", "88", "15"}
    };

    private final MembershipService membershipService;

    public TopupServiceImpl(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Override
    public TopupProgressDTO getTopupProgress(Long userId, Double amount, String couponCode) {
        double current = amount != null ? amount : 0;
        TopupProgressDTO dto = new TopupProgressDTO();
        dto.setReached(true);
        dto.setThreshold(0.0);
        dto.setGap(0.0);
        dto.setCouponName("");
        dto.setCouponCode("");
        dto.setDiscount(0.0);

        // 空购物袋：无可凑金额，直接返回"已达标"（前端空购物袋不渲染进度条）
        if (current <= 0) {
            return dto;
        }

        // 已选券：按该券门槛计算（固定权益券；卡券包券当前仅展示不参与下单，前端也只会传固定券 code）
        if (couponCode != null && !couponCode.isBlank()) {
            for (String[] c : FIXED_COUPONS) {
                if (c[0].equals(couponCode)) {
                    double threshold = Double.parseDouble(c[2]);
                    double discount = Double.parseDouble(c[3]);
                    dto.setThreshold(threshold);
                    dto.setDiscount(discount);
                    if (current + 1e-9 >= threshold) {
                        dto.setReached(true);
                        dto.setGap(0.0);
                    } else {
                        dto.setReached(false);
                        dto.setGap(round2(threshold - current));
                    }
                    dto.setCouponName(c[1]);
                    dto.setCouponCode(c[0]);
                    return dto;
                }
            }
            return dto;
        }

        // 未选券：卡券包未使用满减券（登录用户）优先，其次固定权益券；取 minimum > amount 的最小门槛
        List<double[]> candidates = new ArrayList<>(); // [threshold, discount]
        List<String> names = new ArrayList<>();
        if (userId != null && userId > 0) {
            List<VoucherDTO> vouchers = membershipService.listVouchers(userId);
            for (VoucherDTO v : vouchers) {
                if (v.getStatus() != null && v.getStatus() == 0
                        && v.getMinimum() != null && v.getMinimum() > 0) {
                    candidates.add(new double[]{v.getMinimum(), v.getDiscount() != null ? v.getDiscount() : 0});
                    names.add(v.getName() != null ? v.getName() : "卡券包券");
                }
            }
        }
        for (String[] c : FIXED_COUPONS) {
            candidates.add(new double[]{Double.parseDouble(c[2]), Double.parseDouble(c[3])});
            names.add(c[1]);
        }

        double bestThreshold = Double.MAX_VALUE;
        int bestIdx = -1;
        for (int i = 0; i < candidates.size(); i++) {
            double t = candidates.get(i)[0];
            if (t > current + 1e-9 && t < bestThreshold) {
                bestThreshold = t;
                bestIdx = i;
            }
        }
        if (bestIdx < 0) {
            // 已满足全部候选门槛
            double maxThreshold = 0;
            for (double[] c : candidates) {
                if (c[0] > maxThreshold) maxThreshold = c[0];
            }
            dto.setReached(true);
            dto.setThreshold(maxThreshold);
            dto.setGap(0.0);
            dto.setCouponName("");
            dto.setCouponCode("");
            return dto;
        }
        dto.setReached(false);
        dto.setThreshold(bestThreshold);
        dto.setGap(round2(bestThreshold - current));
        dto.setDiscount(candidates.get(bestIdx)[1]);
        dto.setCouponName(names.get(bestIdx));
        // 卡券包券无 code，仅固定券填 code（前端展示不需要 code，下单优惠也仅固定券）
        dto.setCouponCode("");
        return dto;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
