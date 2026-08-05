package com.coffee.module.member.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

/**
 * 会员 Mapper
 */
@Mapper
public interface MemberMapper extends BaseMapper<MemberPO> {

    @Select("SELECT * FROM coffee_user WHERE openid = #{openid}")
    MemberPO selectByOpenid(@Param("openid") String openid);

    @Update("UPDATE coffee_user SET total_spent = #{totalSpent}, member_level = " +
            "CASE WHEN #{totalSpent} >= 500 THEN 'SVIP' WHEN #{totalSpent} >= 100 THEN 'VIP' ELSE 'REGULAR' END " +
            "WHERE id = #{id}")
    void updateTotalSpent(@Param("id") Long id, @Param("totalSpent") double totalSpent);

    @Update("UPDATE coffee_user SET points = #{points}, points_level = " +
            "CASE WHEN #{points} >= 1000 THEN 'GOLD' WHEN #{points} >= 500 THEN 'SILVER' ELSE 'BRONZE' END " +
            "WHERE id = #{id}")
    void updatePoints(@Param("id") Long id, @Param("points") int points);
}
