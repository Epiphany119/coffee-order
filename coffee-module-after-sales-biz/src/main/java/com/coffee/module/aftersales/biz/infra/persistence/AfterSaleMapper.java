package com.coffee.module.aftersales.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 售后单 Mapper
 */
@Mapper
public interface AfterSaleMapper extends BaseMapper<AfterSalePO> {

    /** 按用户查售后单（含订单号/商品名快照，按创建时间倒序） */
    @Select("SELECT a.*, o.order_no, o.beverage_name " +
            "FROM after_sale a LEFT JOIN user_order o ON a.order_id = o.id " +
            "WHERE a.user_id = #{userId} ORDER BY a.created_at DESC")
    List<AfterSaleVO> selectByUserId(@Param("userId") Long userId);

    @Select("<script>SELECT a.*, o.order_no, o.beverage_name FROM after_sale a " +
            "INNER JOIN user_order o ON a.order_id = o.id WHERE o.store_id = #{storeId} " +
            "<if test='status != null and status != \"\"'>AND a.status = #{status}</if> " +
            "ORDER BY a.created_at DESC</script>")
    List<AfterSaleVO> selectByStoreId(@Param("storeId") Long storeId, @Param("status") String status);
}
