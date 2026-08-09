package com.coffee.module.aftersales.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单反馈 Mapper
 */
@Mapper
public interface FeedbackMapper extends BaseMapper<FeedbackPO> {

    /** 按用户查反馈记录（含订单信息 + 用户名，按创建时间倒序） */
    @Select("SELECT f.*, o.order_no, o.beverage_name, u.username AS username " +
            "FROM feedback f " +
            "LEFT JOIN user_order o ON f.order_id = o.id " +
            "LEFT JOIN coffee_user u ON f.user_id = u.id " +
            "WHERE f.user_id = #{userId} ORDER BY f.created_at DESC")
    List<FeedbackVO> selectByUserId(@Param("userId") Long userId);

    /** 按订单查反馈记录（商家端订单明细展示用，按创建时间倒序） */
    @Select("SELECT f.*, o.order_no, o.beverage_name, u.username AS username " +
            "FROM feedback f " +
            "LEFT JOIN user_order o ON f.order_id = o.id " +
            "LEFT JOIN coffee_user u ON f.user_id = u.id " +
            "WHERE f.order_id = #{orderId} ORDER BY f.created_at DESC")
    List<FeedbackVO> selectByOrderId(@Param("orderId") Long orderId);

    /** 按商品 id 查反馈记录（点餐界面商品下方展示，按创建时间倒序） */
    @Select("SELECT f.*, o.order_no, o.beverage_name, u.username AS username " +
            "FROM feedback f " +
            "LEFT JOIN user_order o ON f.order_id = o.id " +
            "LEFT JOIN coffee_user u ON f.user_id = u.id " +
            "WHERE f.product_id = #{productId} ORDER BY f.created_at DESC")
    List<FeedbackVO> selectByProductId(@Param("productId") Long productId);
}
