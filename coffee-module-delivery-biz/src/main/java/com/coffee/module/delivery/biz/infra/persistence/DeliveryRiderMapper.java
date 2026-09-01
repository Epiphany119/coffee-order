package com.coffee.module.delivery.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DeliveryRiderMapper extends BaseMapper<DeliveryRiderPO> {

    @Select("SELECT * FROM delivery_rider WHERE username = #{username}")
    DeliveryRiderPO selectByUsername(@Param("username") String username);

    /** 资料页显式更新，允许清空可选字段。 */
    @Update("UPDATE delivery_rider SET nickname = #{nickname}, phone = #{phone}, birthday = #{birthday}, "
            + "email = #{email}, other_info = #{otherInfo}, updated_at = NOW() WHERE id = #{id}")
    void updateProfile(@Param("id") Long id,
                       @Param("nickname") String nickname,
                       @Param("phone") String phone,
                       @Param("birthday") java.time.LocalDate birthday,
                       @Param("email") String email,
                       @Param("otherInfo") String otherInfo);

    @Update("UPDATE delivery_rider SET avatar_url = #{avatarUrl}, updated_at = NOW() WHERE id = #{id}")
    void updateAvatar(@Param("id") Long id, @Param("avatarUrl") String avatarUrl);
}
