package com.coffee.module.auth.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {

    @Select("SELECT * FROM coffee_user WHERE username = #{username}")
    UserPO selectByUsername(@Param("username") String username);

    @Select("SELECT COUNT(*) FROM coffee_user WHERE username = #{username}")
    long countByUsername(@Param("username") String username);

    @Select("SELECT password FROM coffee_user WHERE id = #{id}")
    String selectPasswordById(@Param("id") Long id);

    @Update("UPDATE coffee_user SET password = #{password} WHERE id = #{id}")
    void updatePassword(@Param("id") Long id, @Param("password") String password);
}
