package com.coffee.module.membership.api;

import com.coffee.module.membership.api.dto.*;

import java.util.List;

/**
 * 会员体系服务接口（会员等级 / 积分兑换 / 会员权益）
 *
 * 规则说明（当前默认规则，后续由业务制定，集中在 MembershipDomainService 常量中）：
 * - 等级：REGULAR（普通）→ VIP（累计消费 ≥100，95 折）→ SVIP（累计消费 ≥500，9 折）
 * - 积分：消费按实付金额加分，实付 1 元 = 1 分 × 等级倍率（REGULAR ×1.0 / VIP ×1.1 / SVIP ×1.2）
 * - 权益券：FIKA8（满48减8）、SWEET12（满78减12）、BEAN15（满88减15），会员每月可领
 * - 积分兑换：500 分 = 5 张 10 元无门槛券；1000 分 = 5 张 12 元 + 4 张 10 元无门槛券
 */
public interface MembershipService {

    /** 查询会员卡（未开卡返回 null，可调 initCard 开卡） */
    MemberCardDTO getCard(Long userId);

    /** 开卡（幂等）：从 coffee_user 现有消费/积分初始化会员卡快照 */
    MemberCardDTO initCard(Long userId);

    /** 会员权益列表：等级折扣 + 可用权益券 */
    List<BenefitDTO> listBenefits(Long userId);

    /** 等级规则（阈值/折扣，供前端展示与管理） */
    LevelRuleDTO getLevelRules();

    /** 积分兑换项列表（接口下发，前端免硬编码） */
    List<RedeemItemDTO> getRedeemItems();

    /** 积分兑换（拆券发放到用户卡券包） */
    RedeemResultDTO redeemPoints(Long userId, String itemCode);

    /** 用户卡券包（未使用在前） */
    List<VoucherDTO> listVouchers(Long userId);

    /**
     * 消费积分入账：订单完成时按实付金额加分（实付 1 元 = 1 分 × 等级倍率）
     * 倍率：REGULAR ×1.0 / VIP ×1.1 / SVIP ×1.2
     */
    void addConsumptionPoints(Long userId, double paidAmount);

    /**
     * 消费积分扣回：取消已完成订单时按实付金额扣分（镜像 addConsumptionPoints）
     * 先由调用方扣减累计消费，本方法按回滚后的 total_spent 重算等级与倍率；
     * 积分已被兑换消耗时最多扣到 0，不出现负分。
     */
    void deductConsumptionPoints(Long userId, double paidAmount);
}
