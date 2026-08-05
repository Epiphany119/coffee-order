package com.coffee.module.member.api.dto;

import lombok.Data;

/**
 * 会员等级 DTO
 */
@Data
public class MemberLevelDTO {
    private String level;
    private String label;
    private Double discountRate;
    private Double totalSpent;

    public static final double VIP_THRESHOLD = 100;
    public static final double SVIP_THRESHOLD = 500;

    public String getLabel() {
        return switch (level) {
            case "SVIP" -> "SVIP会员";
            case "VIP" -> "VIP会员";
            default -> "普通会员";
        };
    }

    public Double getDiscountRate() {
        return switch (level) {
            case "SVIP" -> 0.90;
            case "VIP" -> 0.95;
            default -> 1.0;
        };
    }

    public static MemberLevelDTO fromTotalSpent(double totalSpent) {
        MemberLevelDTO dto = new MemberLevelDTO();
        dto.setTotalSpent(totalSpent);
        if (totalSpent >= SVIP_THRESHOLD) {
            dto.setLevel("SVIP");
        } else if (totalSpent >= VIP_THRESHOLD) {
            dto.setLevel("VIP");
        } else {
            dto.setLevel("REGULAR");
        }
        return dto;
    }
}
