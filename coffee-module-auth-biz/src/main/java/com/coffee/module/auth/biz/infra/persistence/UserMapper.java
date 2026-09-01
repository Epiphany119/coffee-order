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

    @Update("UPDATE coffee_user SET last_store_id = #{storeId} WHERE id = #{id}")
    void updateLastStore(@Param("id") Long id, @Param("storeId") Long storeId);

    /**
     * 资料页使用显式更新，允许用户把可选资料清空。
     * MyBatis-Plus 的 updateById 默认会忽略 null，这里不能让旧的邮箱/联系方式残留。
     */
    @Update("UPDATE coffee_user SET nickname = #{nickname}, phone = #{phone}, birthday = #{birthday}, "
            + "wechat_id = #{wechatId}, qq_number = #{qqNumber}, email = #{email}, other_info = #{otherInfo} "
            + "WHERE id = #{id}")
    void updateProfile(@Param("id") Long id,
                       @Param("nickname") String nickname,
                       @Param("phone") String phone,
                       @Param("birthday") java.time.LocalDate birthday,
                       @Param("wechatId") String wechatId,
                       @Param("qqNumber") String qqNumber,
                       @Param("email") String email,
                       @Param("otherInfo") String otherInfo);

    @Update("UPDATE coffee_user SET avatar_url = #{avatarUrl} WHERE id = #{id}")
    void updateAvatar(@Param("id") Long id, @Param("avatarUrl") String avatarUrl);
}
