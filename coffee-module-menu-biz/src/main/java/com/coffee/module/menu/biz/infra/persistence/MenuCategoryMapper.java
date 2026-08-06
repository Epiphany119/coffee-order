package com.coffee.module.menu.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商品分类 Mapper
 *
 * store_id 归属：0=共享类目（所有店可见），N=该商家创建的自定义类目（仅本店可见）。
 * 店铺可见类目 = 共享类目 + 该店自定义类目。
 */
@Mapper
public interface MenuCategoryMapper extends BaseMapper<MenuCategoryPO> {

    /** 店铺可见类目：共享类目在前（按 sort_order），自定义类目在后（按 id） */
    @Select("SELECT * FROM menu_category WHERE store_id = 0 OR store_id = #{storeId} " +
            "ORDER BY (store_id = 0) DESC, sort_order, id")
    List<MenuCategoryPO> selectByStore(@Param("storeId") Long storeId);

    /** 按编码查类目 */
    @Select("SELECT * FROM menu_category WHERE code = #{code} LIMIT 1")
    MenuCategoryPO selectByCode(@Param("code") String code);
}
