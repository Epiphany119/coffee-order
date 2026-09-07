package com.coffee.module.auth.api.dto;

/** 邮箱验证码的业务用途，验证码不可跨用途复用。 */
public enum EmailCodePurpose {
    LOGIN,
    REGISTER,
    BIND,
    PASSWORD_CHANGE
}
