package com.coffee.module.menu.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户收藏 Mapper
 */
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavoritePO> {

    @Select("SELECT * FROM user_favorite WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<UserFavoritePO> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_favorite WHERE guest_id = #{guestId} ORDER BY created_at DESC")
    List<UserFavoritePO> selectByGuestId(@Param("guestId") String guestId);

    @Select("SELECT COUNT(*) FROM user_favorite WHERE user_id = #{userId} AND product_code = #{productCode}")
    long countByUserIdAndCode(@Param("userId") Long userId, @Param("productCode") String productCode);

    @Select("SELECT COUNT(*) FROM user_favorite WHERE guest_id = #{guestId} AND product_code = #{productCode}")
    long countByGuestIdAndCode(@Param("guestId") String guestId, @Param("productCode") String productCode);

    @Delete("DELETE FROM user_favorite WHERE user_id = #{userId} AND product_code = #{productCode}")
    int deleteByUserIdAndCode(@Param("userId") Long userId, @Param("productCode") String productCode);

    @Delete("DELETE FROM user_favorite WHERE guest_id = #{guestId} AND product_code = #{productCode}")
    int deleteByGuestIdAndCode(@Param("guestId") String guestId, @Param("productCode") String productCode);

    @Delete("DELETE FROM user_favorite WHERE guest_id = #{guestId}")
    int deleteByGuestId(@Param("guestId") String guestId);
}
