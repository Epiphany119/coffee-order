package com.coffee.module.store.biz.domain.repository;

import com.coffee.module.store.biz.domain.Merchant;

import java.util.Optional;

/**
 * 商家仓储接口
 */
public interface MerchantRepository {

    Optional<Merchant> findById(Long id);

    Optional<Merchant> findByMerchantNo(String merchantNo);

    Optional<Merchant> findByUsername(String username);

    /** 按绑定的店铺 id 查询（入驻现有店铺时找占位商家记录） */
    Optional<Merchant> findByStoreId(Long storeId);

    boolean existsByMerchantNo(String merchantNo);

    boolean existsByUsername(String username);

    /** 按登录账号删除（入驻现有店铺时废弃注册创建的未绑定记录，避免用户名唯一冲突） */
    void deleteByUsername(String username);

    Merchant save(Merchant merchant);
    void updateProfile(Merchant merchant);
    void updateAvatar(Long id, String avatarUrl);
    void updateBusinessLicense(Long id, String licenseUrl);
}
