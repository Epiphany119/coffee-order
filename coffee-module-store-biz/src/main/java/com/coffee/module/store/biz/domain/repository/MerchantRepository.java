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

    boolean existsByMerchantNo(String merchantNo);

    boolean existsByUsername(String username);

    Merchant save(Merchant merchant);
}
