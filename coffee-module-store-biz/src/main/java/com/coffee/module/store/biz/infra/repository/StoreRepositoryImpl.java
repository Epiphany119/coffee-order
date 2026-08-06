package com.coffee.module.store.biz.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coffee.module.store.biz.domain.Store;
import com.coffee.module.store.biz.domain.repository.StoreRepository;
import com.coffee.module.store.biz.infra.persistence.StoreMapper;
import com.coffee.module.store.biz.infra.persistence.StorePO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 店铺仓储实现
 */
@Repository
public class StoreRepositoryImpl implements StoreRepository {

    private final StoreMapper storeMapper;

    public StoreRepositoryImpl(StoreMapper storeMapper) {
        this.storeMapper = storeMapper;
    }

    @Override
    public Optional<Store> findById(Long id) {
        StorePO po = storeMapper.selectById(id);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<Store> findByCode(String code) {
        StorePO po = storeMapper.selectByCode(code);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public List<Store> findAll() {
        return storeMapper.selectList(
                        new LambdaQueryWrapper<StorePO>().orderByAsc(StorePO::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Store> findAvailable() {
        return storeMapper.selectAvailable().stream()
                .map(this::toDomain).toList();
    }

    @Override
    public List<Store> findOpen() {
        return storeMapper.selectOpen().stream()
                .map(this::toDomain).toList();
    }

    @Override
    public List<Store> findByMerchantId(Long merchantId) {
        return storeMapper.selectByMerchantId(merchantId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public boolean existsByCode(String code) {
        return storeMapper.selectCount(
                new LambdaQueryWrapper<StorePO>().eq(StorePO::getCode, code)) > 0;
    }

    @Override
    public long count() {
        return storeMapper.selectCount(null);
    }

    @Override
    public Store save(Store store) {
        StorePO po = toPO(store);
        if (store.getId() == null) {
            storeMapper.insert(po);
            store.setId(po.getId());
        } else {
            storeMapper.updateById(po);
        }
        return store;
    }

    @Override
    public void saveAll(List<Store> stores) {
        for (Store store : stores) {
            storeMapper.insert(toPO(store));
        }
    }

    @Override
    public void deleteById(Long id) {
        storeMapper.deleteById(id);
    }

    private Store toDomain(StorePO po) {
        Store s = new Store();
        s.setId(po.getId());
        s.setCode(po.getCode());
        s.setName(po.getName());
        s.setAddress(po.getAddress());
        s.setPhone(po.getPhone());
        s.setBusinessHours(po.getBusinessHours());
        s.setStatus(po.getStatus());
        s.setMerchantId(po.getMerchantId());
        s.setCreatedAt(po.getCreatedAt());
        s.setUpdatedAt(po.getUpdatedAt());
        return s;
    }

    private StorePO toPO(Store s) {
        StorePO po = new StorePO();
        po.setId(s.getId());
        po.setCode(s.getCode());
        po.setName(s.getName());
        po.setAddress(s.getAddress());
        po.setPhone(s.getPhone());
        po.setBusinessHours(s.getBusinessHours());
        po.setStatus(s.getStatus());
        po.setMerchantId(s.getMerchantId());
        po.setCreatedAt(s.getCreatedAt());
        po.setUpdatedAt(s.getUpdatedAt());
        return po;
    }
}
