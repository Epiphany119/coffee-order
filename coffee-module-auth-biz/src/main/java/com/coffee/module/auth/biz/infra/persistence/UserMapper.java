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

    @Select("SELECT * FROM coffee_user WHERE email = #{email} LIMIT 1")
    UserPO selectByEmail(@Param("email") String email);

    @Select("SELECT COUNT(*) FROM coffee_user WHERE username = #{username}")
    long countByUsername(@Param("username") String username);

    @Select("SELECT COUNT(*) FROM coffee_user WHERE email = #{email}")
    long countByEmail(@Param("email") String email);

    @Select("SELECT password FROM coffee_user WHERE id = #{id}")
    String selectPasswordById(@Param("id") Long id);

    @Update("UPDATE coffee_user SET password = #{password} WHERE id = #{id}")
    void updatePassword(@Param("id") Long id, @Param("password") String password);

    @Update("UPDATE coffee_user SET last_store_id = #{storeId} WHERE id = #{id}")
    void updateLastStore(@Param("id") Long id, @Param("storeId") Long storeId);

    /**
     * 资料页使用显式更新，允许用户把可选资料清空；邮箱不在此处更新，
     * 必须通过绑定邮箱验证码流程修改。
     */
    @Update("UPDATE coffee_user SET nickname = #{nickname}, phone = #{phone}, birthday = #{birthday}, "
            + "wechat_id = #{wechatId}, qq_number = #{qqNumber}, other_info = #{otherInfo} "
            + "WHERE id = #{id}")
    void updateProfile(@Param("id") Long id,
                       @Param("nickname") String nickname,
                       @Param("phone") String phone,
                       @Param("birthday") java.time.LocalDate birthday,
                       @Param("wechatId") String wechatId,
                       @Param("qqNumber") String qqNumber,
                       @Param("otherInfo") String otherInfo);

    @Update("UPDATE coffee_user SET email = #{email} WHERE id = #{id}")
    void updateEmail(@Param("id") Long id, @Param("email") String email);

    @Update("UPDATE coffee_user SET avatar_url = #{avatarUrl} WHERE id = #{id}")
    void updateAvatar(@Param("id") Long id, @Param("avatarUrl") String avatarUrl);
}
