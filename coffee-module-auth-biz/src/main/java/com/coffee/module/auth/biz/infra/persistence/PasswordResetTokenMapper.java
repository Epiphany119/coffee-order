package com.coffee.module.auth.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 密码重置令牌 Mapper
 */
@Mapper
public interface PasswordResetTokenMapper extends BaseMapper<PasswordResetTokenPO> {

    @Select("SELECT * FROM password_reset_token WHERE token = #{token}")
    PasswordResetTokenPO selectByToken(@Param("token") String token);

    @Update("UPDATE password_reset_token SET used = 1 WHERE user_id = #{userId} AND used = 0")
    void invalidateByUserId(@Param("userId") Long userId);
}
