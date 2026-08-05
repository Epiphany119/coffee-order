package com.coffee.module.store.biz.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coffee.module.store.biz.domain.Merchant;
import com.coffee.module.store.biz.domain.repository.MerchantRepository;
import com.coffee.module.store.biz.infra.persistence.MerchantMapper;
import com.coffee.module.store.biz.infra.persistence.MerchantPO;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 商家仓储实现
 */
@Repository
public class MerchantRepositoryImpl implements MerchantRepository {

    private final MerchantMapper merchantMapper;

    public MerchantRepositoryImpl(MerchantMapper merchantMapper) {
        this.merchantMapper = merchantMapper;
    }

    @Override
    public Optional<Merchant> findById(Long id) {
        MerchantPO po = merchantMapper.selectById(id);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<Merchant> findByMerchantNo(String merchantNo) {
        MerchantPO po = merchantMapper.selectByMerchantNo(merchantNo);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<Merchant> findByUsername(String username) {
        MerchantPO po = merchantMapper.selectByUsername(username);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public boolean existsByMerchantNo(String merchantNo) {
        return merchantMapper.selectCount(
                new LambdaQueryWrapper<MerchantPO>().eq(MerchantPO::getMerchantNo, merchantNo)) > 0;
    }

    @Override
    public boolean existsByUsername(String username) {
        return merchantMapper.selectCount(
                new LambdaQueryWrapper<MerchantPO>().eq(MerchantPO::getUsername, username)) > 0;
    }

    @Override
    public Merchant save(Merchant merchant) {
        MerchantPO po = toPO(merchant);
        if (merchant.getId() == null) {
            merchantMapper.insert(po);
            merchant.setId(po.getId());
        } else {
            merchantMapper.updateById(po);
        }
        return merchant;
    }

    private Merchant toDomain(MerchantPO po) {
        Merchant m = new Merchant();
        m.setId(po.getId());
        m.setMerchantNo(po.getMerchantNo());
        m.setUsername(po.getUsername());
        m.setPasswordHash(po.getPassword());
        m.setNickname(po.getNickname());
        m.setPhone(po.getPhone());
        m.setStatus(po.getStatus());
        m.setCreatedAt(po.getCreatedAt());
        m.setUpdatedAt(po.getUpdatedAt());
        return m;
    }

    private MerchantPO toPO(Merchant m) {
        MerchantPO po = new MerchantPO();
        po.setId(m.getId());
        po.setMerchantNo(m.getMerchantNo());
        po.setUsername(m.getUsername());
        po.setPassword(m.getPasswordHash());
        po.setNickname(m.getNickname());
        po.setPhone(m.getPhone());
        po.setStatus(m.getStatus());
        po.setCreatedAt(m.getCreatedAt());
        po.setUpdatedAt(m.getUpdatedAt());
        return po;
    }
}
