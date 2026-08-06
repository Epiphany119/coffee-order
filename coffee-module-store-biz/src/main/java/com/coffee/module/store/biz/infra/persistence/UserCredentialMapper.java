package com.coffee.module.store.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户端账号凭证 Mapper（查询 coffee_user，用于商家注册校验）
 */
public interface UserCredentialMapper extends BaseMapper<UserCredentialPO> {

    /** 按用户名查用户凭证（不存在返回 null） */
    @Select("SELECT id, username, password, merchant_no FROM coffee_user WHERE username = #{username} LIMIT 1")
    UserCredentialPO selectByUsername(@Param("username") String username);

    /** 回填用户端账号绑定的商家编号（入驻现有店铺时写入） */
    @Update("UPDATE coffee_user SET merchant_no = #{merchantNo} WHERE id = #{userId}")
    int updateMerchantNo(@Param("userId") Long userId, @Param("merchantNo") String merchantNo);
}
