package com.coffee.module.membership.biz.domain.service;

import com.coffee.module.member.api.MemberService;
import com.coffee.module.member.api.dto.MemberDTO;
import com.coffee.module.membership.api.MembershipService;
import com.coffee.module.membership.api.dto.*;
import com.coffee.module.membership.biz.domain.MemberCard;
import com.coffee.module.membership.biz.domain.UserVoucher;
import com.coffee.module.membership.biz.domain.repository.MembershipRepository;
import com.coffee.module.membership.biz.domain.repository.VoucherRepository;
import com.coffee.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 会员体系领域服务
 *
 * ⚠️ 规则集中区：所有等级/积分/兑换/权益规则都在下方常量中，后续业务制定规则只需改这里
 */
@Service
public class MembershipDomainService implements MembershipService {

    // ==================== 等级规则 ====================
    /** VIP 门槛：累计消费 ≥ 100 元 */
    public static final double VIP_THRESHOLD = 100;
    /** SVIP 门槛：累计消费 ≥ 500 元 */
    public static final double SVIP_THRESHOLD = 500;
    /** VIP 折扣率（95 折） */
    public static final double VIP_DISCOUNT_RATE = 0.95;
    /** SVIP 折扣率（9 折） */
    public static final double SVIP_DISCOUNT_RATE = 0.90;

    // ==================== 积分规则 ====================
    /** 消费积分：实付 1 元 = 1 积分（基准） */
    public static final double POINTS_PER_YUAN = 1.0;
    /** 等级倍率：REGULAR 1 元 1 分 / VIP 1 元 1.1 分 / SVIP 1 元 1.2 分 */
    public static final double POINT_RATE_REGULAR = 1.0;
    public static final double POINT_RATE_VIP = 1.1;
    public static final double POINT_RATE_SVIP = 1.2;
    /** 积分等级：SILVER ≥ 500 分 */
    public static final int SILVER_POINTS = 500;
    /** 积分等级：GOLD ≥ 1000 分 */
    public static final int GOLD_POINTS = 1000;

    // ==================== 权益规则（等级折扣之外的会员权益券） ====================
    public record BenefitRule(String code, String name, double minimum, double discount, String description) {
    }

    public static final List<BenefitRule> BENEFIT_RULES = List.of(
            new BenefitRule("FIKA8", "下午茶券", 48, 8, "下午茶品类满 ¥48 立减 ¥8"),
            new BenefitRule("SWEET12", "甜品券", 78, 12, "甜品品类满 ¥78 立减 ¥12"),
            new BenefitRule("BEAN15", "咖啡券", 88, 15, "咖啡品类满 ¥88 立减 ¥15")
    );

    // ==================== 积分兑换规则（兑换后按张发放到用户卡券包） ====================
    /** 单张券面额 */
    public record VoucherGrant(String name, double discount, int count) {
    }

    public record RedeemRule(String code, String name, int costPoints, List<VoucherGrant> grants) {
    }

    public static final List<RedeemRule> REDEEM_RULES = List.of(
            new RedeemRule("R500", "10 元无门槛券 ×5", 500,
                    List.of(new VoucherGrant("10 元无门槛券", 10, 5))),
            new RedeemRule("R1000", "12 元无门槛券 ×5 + 10 元无门槛券 ×4", 1000,
                    List.of(new VoucherGrant("12 元无门槛券", 12, 5),
                            new VoucherGrant("10 元无门槛券", 10, 4)))
    );

    private final MembershipRepository membershipRepository;
    private final VoucherRepository voucherRepository;
    private final MemberService memberService;

    public MembershipDomainService(MembershipRepository membershipRepository,
                                   VoucherRepository voucherRepository,
                                   MemberService memberService) {
        this.membershipRepository = membershipRepository;
        this.voucherRepository = voucherRepository;
        this.memberService = memberService;
    }

    @Override
    public MemberCardDTO getCard(Long userId) {
        if (userId == null || userId <= 0) return null;
        MemberCard card = membershipRepository.findByUserId(userId);
        return card != null ? toDTO(card) : null;
    }

    @Override
    public MemberCardDTO initCard(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ServiceException(400, "无效用户");
        }
        // 幂等：已有卡直接返回
        MemberCard existing = membershipRepository.findByUserId(userId);
        if (existing != null) {
            return toDTO(existing);
        }
        // 从 coffee_user 快照当前消费/积分开卡
        MemberDTO member = memberService.getMember(userId);
        if (member == null) {
            throw new ServiceException(404, "会员不存在");
        }
        MemberCard card = new MemberCard();
        card.setUserId(userId);
        card.setCardNo("C" + String.format("%06d", userId));
        card.setLevel(resolveLevel(member.getTotalSpent() != null ? member.getTotalSpent() : 0));
        card.setPoints(member.getPoints() != null ? member.getPoints() : 0);
        card.setTotalSpent(member.getTotalSpent() != null ? member.getTotalSpent() : 0);
        card.setExchangePoints(0);
        card.setStatus(MemberCard.STATUS_ACTIVE);
        card.setCreatedAt(LocalDateTime.now());
        card.setUpdatedAt(LocalDateTime.now());
        membershipRepository.save(card);
        return toDTO(card);
    }

    @Override
    public List<BenefitDTO> listBenefits(Long userId) {
        MemberDTO member = userId != null && userId > 0 ? memberService.getMember(userId) : null;
        double totalSpent = member != null && member.getTotalSpent() != null ? member.getTotalSpent() : 0;

        List<BenefitDTO> benefits = new ArrayList<>();
        // 等级折扣权益
        BenefitDTO discount = new BenefitDTO();
        discount.setCode("LEVEL_DISCOUNT");
        discount.setName("会员等级折扣");
        discount.setType("LEVEL");
        discount.setDiscountRate(resolveDiscountRate(totalSpent));
        discount.setMinimum(0.0);
        discount.setDiscount(0.0);
        discount.setDescription(levelLabel(totalSpent) + "：全场订单享 " + Math.round(resolveDiscountRate(totalSpent) * 100) + " 折");
        benefits.add(discount);

        // 权益券（每月可领）
        for (BenefitRule rule : BENEFIT_RULES) {
            BenefitDTO dto = new BenefitDTO();
            dto.setCode(rule.code());
            dto.setName(rule.name());
            dto.setType("VOUCHER");
            dto.setDiscountRate(1.0);
            dto.setMinimum(rule.minimum());
            dto.setDiscount(rule.discount());
            dto.setDescription(rule.description());
            benefits.add(dto);
        }
        return benefits;
    }

    @Override
    public LevelRuleDTO getLevelRules() {
        LevelRuleDTO dto = new LevelRuleDTO();
        List<LevelRuleDTO.LevelItem> levels = new ArrayList<>();
        levels.add(levelItem("REGULAR", "普通会员", 0, 1.0));
        levels.add(levelItem("VIP", "VIP会员", VIP_THRESHOLD, VIP_DISCOUNT_RATE));
        levels.add(levelItem("SVIP", "SVIP会员", SVIP_THRESHOLD, SVIP_DISCOUNT_RATE));
        dto.setLevels(levels);
        dto.setPointsRule("消费 1 元 = 1 积分；积分等级：BRONZE / SILVER（≥" + SILVER_POINTS + "）/ GOLD（≥" + GOLD_POINTS + "）");
        return dto;
    }

    private LevelRuleDTO.LevelItem levelItem(String level, String label, double threshold, double discountRate) {
        LevelRuleDTO.LevelItem item = new LevelRuleDTO.LevelItem();
        item.setLevel(level);
        item.setLabel(label);
        item.setThreshold(threshold);
        item.setDiscountRate(discountRate);
        return item;
    }

    @Override
    public List<RedeemItemDTO> getRedeemItems() {
        List<RedeemItemDTO> items = new ArrayList<>();
        for (RedeemRule rule : REDEEM_RULES) {
            RedeemItemDTO dto = new RedeemItemDTO();
            dto.setCode(rule.code());
            dto.setName(rule.name());
            dto.setCostPoints(rule.costPoints());
            List<RedeemItemDTO.Grant> grants = new ArrayList<>();
            for (VoucherGrant g : rule.grants()) {
                RedeemItemDTO.Grant grant = new RedeemItemDTO.Grant();
                grant.setName(g.name());
                grant.setDiscount(g.discount());
                grant.setCount(g.count());
                grants.add(grant);
            }
            dto.setGrants(grants);
            items.add(dto);
        }
        return items;
    }

    @Override
    public RedeemResultDTO redeemPoints(Long userId, String itemCode) {
        if (userId == null || userId <= 0) {
            throw new ServiceException(400, "无效用户");
        }
        RedeemRule rule = REDEEM_RULES.stream()
                .filter(r -> r.code().equalsIgnoreCase(itemCode))
                .findFirst()
                .orElseThrow(() -> new ServiceException(400, "兑换项不存在: " + itemCode));

        MemberCard card = membershipRepository.findByUserId(userId);
        if (card == null) {
            card = toDomain(initCard(userId));
        }
        if (card.getStatus() == null || card.getStatus() != MemberCard.STATUS_ACTIVE) {
            throw new ServiceException(400, "会员卡已冻结，无法兑换");
        }
        if (!card.redeemPoints(rule.costPoints())) {
            throw new ServiceException(400, "积分不足，需要 " + rule.costPoints() + " 分，当前 " + card.getPoints() + " 分");
        }
        card.setUpdatedAt(LocalDateTime.now());
        membershipRepository.save(card);

        // 按规则拆券发放到卡券包（每张券独立券码）
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int seq = 0;
        List<UserVoucher> vouchers = new ArrayList<>();
        for (VoucherGrant grant : rule.grants()) {
            for (int i = 0; i < grant.count(); i++) {
                UserVoucher v = new UserVoucher();
                v.setUserId(userId);
                v.setVoucherNo(rule.code() + "-" + stamp + "-" + (++seq));
                v.setName(grant.name());
                v.setDiscount(grant.discount());
                v.setMinimum(0.0); // 无门槛
                v.setStatus(UserVoucher.STATUS_UNUSED);
                v.setSource(UserVoucher.SOURCE_REDEEM);
                v.setCreatedAt(LocalDateTime.now());
                vouchers.add(v);
            }
        }
        voucherRepository.saveAll(vouchers);

        RedeemResultDTO result = new RedeemResultDTO();
        result.setItemCode(rule.code());
        result.setItemName(rule.name());
        result.setCostPoints(rule.costPoints());
        result.setRemainingPoints(card.getPoints());
        result.setVouchers(vouchers.stream().map(this::toVoucherDTO).toList());
        result.setMessage("兑换成功，已发放 " + vouchers.size() + " 张券到卡券包");
        return result;
    }

    @Override
    public List<VoucherDTO> listVouchers(Long userId) {
        if (userId == null || userId <= 0) return new ArrayList<>();
        return voucherRepository.findByUserId(userId).stream().map(this::toVoucherDTO).toList();
    }

    @Override
    public void addConsumptionPoints(Long userId, double paidAmount) {
        if (userId == null || userId <= 0 || paidAmount <= 0) return;
        MemberCard card = membershipRepository.findByUserId(userId);
        if (card == null) {
            card = toDomain(initCard(userId));
        }
        if (card.getStatus() == null || card.getStatus() != MemberCard.STATUS_ACTIVE) {
            return; // 冻结卡不积分
        }
        // 同步最新累计消费并重算等级（消费升级即时生效），等级变化后倍率同步变化
        double totalSpent = memberService.getTotalSpent(userId);
        card.setTotalSpent(totalSpent);
        card.setLevel(resolveLevel(totalSpent));

        int earned = (int) Math.round(paidAmount * POINTS_PER_YUAN * pointRate(card.getLevel()));
        if (earned <= 0) return;
        card.setPoints((card.getPoints() == null ? 0 : card.getPoints()) + earned);
        card.setUpdatedAt(LocalDateTime.now());
        membershipRepository.save(card);
    }

    @Override
    public void deductConsumptionPoints(Long userId, double paidAmount) {
        if (userId == null || userId <= 0 || paidAmount <= 0) return;
        MemberCard card = membershipRepository.findByUserId(userId);
        if (card == null) {
            return; // 未开卡无需扣分
        }
        if (card.getStatus() == null || card.getStatus() != MemberCard.STATUS_ACTIVE) {
            return; // 冻结卡不处理
        }
        // 镜像 addConsumptionPoints：按扣回后的累计消费重算等级与倍率，再按当前倍率扣分
        double totalSpent = memberService.getTotalSpent(userId);
        card.setTotalSpent(totalSpent);
        card.setLevel(resolveLevel(totalSpent));

        int deducted = (int) Math.round(paidAmount * POINTS_PER_YUAN * pointRate(card.getLevel()));
        if (deducted <= 0) return;
        int current = card.getPoints() == null ? 0 : card.getPoints();
        // 防负：积分可能已被兑换消耗，最多扣到 0
        card.setPoints(Math.max(0, current - deducted));
        card.setUpdatedAt(LocalDateTime.now());
        membershipRepository.save(card);
    }

    // ==================== 私有工具 ====================

    /** 等级积分倍率：REGULAR ×1.0 / VIP ×1.1 / SVIP ×1.2 */
    private double pointRate(String level) {
        return switch (level == null ? "REGULAR" : level) {
            case "SVIP" -> POINT_RATE_SVIP;
            case "VIP" -> POINT_RATE_VIP;
            default -> POINT_RATE_REGULAR;
        };
    }

    private VoucherDTO toVoucherDTO(UserVoucher v) {
        VoucherDTO dto = new VoucherDTO();
        dto.setId(v.getId());
        dto.setVoucherNo(v.getVoucherNo());
        dto.setName(v.getName());
        dto.setDiscount(v.getDiscount());
        dto.setMinimum(v.getMinimum());
        dto.setStatus(v.getStatus());
        dto.setSource(v.getSource());
        dto.setCreatedAt(v.getCreatedAt());
        dto.setExpiresAt(v.getExpiresAt());
        return dto;
    }

    private String resolveLevel(double totalSpent) {
        if (totalSpent >= SVIP_THRESHOLD) return "SVIP";
        if (totalSpent >= VIP_THRESHOLD) return "VIP";
        return "REGULAR";
    }

    private String levelLabel(double totalSpent) {
        return switch (resolveLevel(totalSpent)) {
            case "SVIP" -> "SVIP会员";
            case "VIP" -> "VIP会员";
            default -> "普通会员";
        };
    }

    private double resolveDiscountRate(double totalSpent) {
        if (totalSpent >= SVIP_THRESHOLD) return SVIP_DISCOUNT_RATE;
        if (totalSpent >= VIP_THRESHOLD) return VIP_DISCOUNT_RATE;
        return 1.0;
    }

    private MemberCard toDomain(MemberCardDTO dto) {
        MemberCard card = new MemberCard();
        card.setId(dto.getId());
        card.setUserId(dto.getUserId());
        card.setCardNo(dto.getCardNo());
        card.setLevel(dto.getLevel());
        card.setPoints(dto.getPoints());
        card.setTotalSpent(dto.getTotalSpent());
        card.setExchangePoints(dto.getExchangePoints());
        card.setStatus(dto.getStatus());
        card.setCreatedAt(dto.getCreatedAt());
        card.setUpdatedAt(dto.getUpdatedAt());
        return card;
    }

    private MemberCardDTO toDTO(MemberCard card) {
        MemberCardDTO dto = new MemberCardDTO();
        dto.setId(card.getId());
        dto.setUserId(card.getUserId());
        dto.setCardNo(card.getCardNo());
        dto.setLevel(card.getLevel());
        dto.setPoints(card.getPoints());
        dto.setTotalSpent(card.getTotalSpent());
        dto.setExchangePoints(card.getExchangePoints());
        dto.setStatus(card.getStatus());
        dto.setDiscountRate(resolveDiscountRate(card.getTotalSpent() != null ? card.getTotalSpent() : 0));
        dto.setCreatedAt(card.getCreatedAt());
        dto.setUpdatedAt(card.getUpdatedAt());
        return dto;
    }
}
