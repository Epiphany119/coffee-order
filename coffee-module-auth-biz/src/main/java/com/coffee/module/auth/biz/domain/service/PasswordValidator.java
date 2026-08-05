package com.coffee.module.auth.biz.domain.service;

import java.util.regex.Pattern;

/**
 * 密码强度验证器
 */
public final class PasswordValidator {
    private static final int MIN_LENGTH = 6;
    private static final Pattern HAS_LETTER = Pattern.compile("[a-zA-Z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");

    private PasswordValidator() {}

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("密码至少需要" + MIN_LENGTH + "个字符");
        }
        if (!HAS_LETTER.matcher(password).find()) {
            throw new IllegalArgumentException("密码必须包含至少一个字母");
        }
        if (!HAS_DIGIT.matcher(password).find()) {
            throw new IllegalArgumentException("密码必须包含至少一个数字");
        }
    }
}
