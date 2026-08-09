package com.coffee.module.membership.biz.domain.repository;

import com.coffee.module.membership.biz.domain.UserVoucher;

import java.util.List;

/**
 * 用户卡券仓储接口
 */
public interface VoucherRepository {

    /** 查询用户卡券包（未使用在前） */
    List<UserVoucher> findByUserId(Long userId);

    /** 批量发放（新增） */
    void saveAll(List<UserVoucher> vouchers);

    UserVoucher findByUserIdAndVoucherNo(Long userId, String voucherNo);

    boolean changeStatus(Long userId, String voucherNo, int expectedStatus, int targetStatus);
}
