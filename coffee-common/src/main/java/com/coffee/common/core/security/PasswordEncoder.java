package com.coffee.common.core.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 密码编码器（封装 Spring Security Crypto）
 * <p>全模块通用：顾客（auth）/ 商家（store）共用
 */
public class PasswordEncoder {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);

    private PasswordEncoder() {}

    /**
     * 加密明文密码
     */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 验证明文与密文是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}
