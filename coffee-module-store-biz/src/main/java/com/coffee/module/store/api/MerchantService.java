package com.coffee.module.store.api;

import com.coffee.module.store.api.dto.MerchantLoginRequest;
import com.coffee.module.store.api.dto.MerchantRegisterRequest;
import com.coffee.module.store.api.dto.MerchantResponse;
import com.coffee.module.store.api.dto.MerchantProfileUpdateRequest;
import com.coffee.module.store.api.dto.MerchantPasswordChangeRequest;

/**
 * 商家服务接口
 */
public interface MerchantService {

    /**
     * 商家注册
     */
    MerchantResponse register(MerchantRegisterRequest request);

    /**
     * 商家登录（BCrypt 校验）
     */
    MerchantResponse login(MerchantLoginRequest request);

    /**
     * 商家信息
     */
    MerchantResponse getMerchant(Long merchantId);

    MerchantResponse updateProfile(Long merchantId, MerchantProfileUpdateRequest request);

    void changePassword(Long merchantId, MerchantPasswordChangeRequest request);
}
