package com.coffee.module.store.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 商家 Mapper
 */
public interface MerchantMapper extends BaseMapper<MerchantPO> {

    /** 按商家编号查询（sj-开头） */
    @Select("SELECT * FROM merchant WHERE merchant_no = #{merchantNo} LIMIT 1")
    MerchantPO selectByMerchantNo(@Param("merchantNo") String merchantNo);

    /** 按登录账号查询（旧版 username，兼容存量数据） */
    @Select("SELECT * FROM merchant WHERE username = #{username} LIMIT 1")
    MerchantPO selectByUsername(@Param("username") String username);
}
