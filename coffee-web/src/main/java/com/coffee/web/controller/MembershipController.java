package com.coffee.web.controller;

import com.coffee.module.membership.api.MembershipService;
import com.coffee.module.membership.api.dto.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会员体系控制器（会员卡 / 等级规则 / 权益 / 积分兑换）
 */
@RestController
@RequestMapping("/api/membership")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    /** 查询会员卡（未开卡返回 null） */
    @GetMapping("/card")
    public MemberCardDTO getCard(@RequestParam Long userId) {
        return membershipService.getCard(userId);
    }

    /** 开卡（幂等，从 coffee_user 快照初始化） */
    @PostMapping("/card/init")
    public MemberCardDTO initCard(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        return membershipService.initCard(userId);
    }

    /** 会员权益列表（等级折扣 + 权益券） */
    @GetMapping("/benefits")
    public List<BenefitDTO> listBenefits(@RequestParam Long userId) {
        return membershipService.listBenefits(userId);
    }

    /** 等级规则（阈值/折扣，供前端展示） */
    @GetMapping("/level-rules")
    public LevelRuleDTO getLevelRules() {
        return membershipService.getLevelRules();
    }

    /** 积分兑换项列表（接口下发，前端免硬编码） */
    @GetMapping("/redeem-items")
    public List<RedeemItemDTO> getRedeemItems() {
        return membershipService.getRedeemItems();
    }

    /** 积分兑换（拆券发放到卡券包） */
    @PostMapping("/points/redeem")
    public RedeemResultDTO redeemPoints(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String itemCode = body.get("itemCode").toString();
        return membershipService.redeemPoints(userId, itemCode);
    }

    /** 用户卡券包 */
    @GetMapping("/vouchers")
    public List<VoucherDTO> listVouchers(@RequestParam Long userId) {
        return membershipService.listVouchers(userId);
    }
}
