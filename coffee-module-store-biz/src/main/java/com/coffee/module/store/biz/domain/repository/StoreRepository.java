package com.coffee.module.store.biz.domain.repository;

import com.coffee.module.store.biz.domain.Store;

import java.util.List;
import java.util.Optional;

/**
 * 店铺仓储接口
 */
public interface StoreRepository {

    Optional<Store> findById(Long id);

    Optional<Store> findByCode(String code);

    List<Store> findAll();

    List<Store> findAvailable();

    List<Store> findOpen();

    List<Store> findByMerchantId(Long merchantId);

    boolean existsByCode(String code);

    long count();

    Store save(Store store);

    void saveAll(List<Store> stores);

    void deleteById(Long id);
}
