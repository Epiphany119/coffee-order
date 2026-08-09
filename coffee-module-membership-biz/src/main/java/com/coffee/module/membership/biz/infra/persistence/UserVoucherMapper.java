package com.coffee.module.membership.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户卡券 Mapper
 */
@Mapper
public interface UserVoucherMapper extends BaseMapper<UserVoucherPO> {
    @Update("UPDATE user_voucher SET status = #{targetStatus} WHERE user_id = #{userId} AND voucher_no = #{voucherNo} AND status = #{expectedStatus}")
    int updateStatus(@Param("userId") Long userId, @Param("voucherNo") String voucherNo,
                     @Param("expectedStatus") int expectedStatus, @Param("targetStatus") int targetStatus);
}
