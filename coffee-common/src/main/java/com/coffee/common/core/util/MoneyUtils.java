package com.coffee.common.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额计算工具
 */
public final class MoneyUtils {

    private MoneyUtils() {}

    public static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static double add(double a, double b) {
        return round2(BigDecimal.valueOf(a).add(BigDecimal.valueOf(b)).doubleValue());
    }

    public static double subtract(double a, double b) {
        return round2(BigDecimal.valueOf(a).subtract(BigDecimal.valueOf(b)).doubleValue());
    }

    public static double multiply(double a, double b) {
        return round2(BigDecimal.valueOf(a).multiply(BigDecimal.valueOf(b)).doubleValue());
    }
}
