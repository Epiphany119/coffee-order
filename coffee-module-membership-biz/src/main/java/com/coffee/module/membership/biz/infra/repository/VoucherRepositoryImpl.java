package com.coffee.module.membership.biz.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coffee.module.membership.biz.domain.UserVoucher;
import com.coffee.module.membership.biz.domain.repository.VoucherRepository;
import com.coffee.module.membership.biz.infra.persistence.UserVoucherMapper;
import com.coffee.module.membership.biz.infra.persistence.UserVoucherPO;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户卡券仓储实现
 */
@Repository
public class VoucherRepositoryImpl implements VoucherRepository {

    private final UserVoucherMapper userVoucherMapper;

    public VoucherRepositoryImpl(UserVoucherMapper userVoucherMapper) {
        this.userVoucherMapper = userVoucherMapper;
    }

    @Override
    public List<UserVoucher> findByUserId(Long userId) {
        List<UserVoucherPO> pos = userVoucherMapper.selectList(new LambdaQueryWrapper<UserVoucherPO>()
                .eq(UserVoucherPO::getUserId, userId)
                .orderByAsc(UserVoucherPO::getStatus)
                .orderByDesc(UserVoucherPO::getId));
        List<UserVoucher> result = new ArrayList<>();
        for (UserVoucherPO po : pos) {
            result.add(toDomain(po));
        }
        return result;
    }

    @Override
    public void saveAll(List<UserVoucher> vouchers) {
        for (UserVoucher v : vouchers) {
            userVoucherMapper.insert(toPO(v));
        }
    }

    private UserVoucher toDomain(UserVoucherPO po) {
        UserVoucher v = new UserVoucher();
        v.setId(po.getId());
        v.setUserId(po.getUserId());
        v.setVoucherNo(po.getVoucherNo());
        v.setName(po.getName());
        v.setDiscount(po.getDiscount());
        v.setMinimum(po.getMinimum());
        v.setStatus(po.getStatus());
        v.setSource(po.getSource());
        v.setCreatedAt(po.getCreatedAt());
        v.setExpiresAt(po.getExpiresAt());
        return v;
    }

    private UserVoucherPO toPO(UserVoucher v) {
        UserVoucherPO po = new UserVoucherPO();
        po.setId(v.getId());
        po.setUserId(v.getUserId());
        po.setVoucherNo(v.getVoucherNo());
        po.setName(v.getName());
        po.setDiscount(v.getDiscount());
        po.setMinimum(v.getMinimum());
        po.setStatus(v.getStatus());
        po.setSource(v.getSource());
        po.setCreatedAt(v.getCreatedAt());
        po.setExpiresAt(v.getExpiresAt());
        return po;
    }
}
