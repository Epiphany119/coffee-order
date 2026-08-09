package com.coffee.module.menu.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 产品 Mapper
 *
 * store_id 即"状态码"：0=初始化的全局商品（所有店共享），N=某店铺专属商品（商家新增/覆盖品）。
 * 店铺菜单 = 全局品 + 该店专属品，同 code 时店铺品覆盖全局品。
 */
@Mapper
public interface MenuItemMapper extends BaseMapper<MenuItemPO> {

    /** 按店+编码查询店铺专属商品（不匹配全局，调用方需自行兜底查全局） */
    @Select("SELECT * FROM menu_item WHERE store_id = #{storeId} AND code = #{code} LIMIT 1")
    MenuItemPO selectByCodeAndStore(@Param("storeId") Long storeId, @Param("code") String code);

    /** 按编码查全局商品（store_id = 0） */
    @Select("SELECT * FROM menu_item WHERE store_id = 0 AND code = #{code} LIMIT 1")
    MenuItemPO selectGlobalByCode(@Param("code") String code);

    /** 按店查可用商品（用户端菜单，下架不可见）：店铺专属 + 未被店铺覆盖的全局品 */
    @Select("SELECT * FROM menu_item p " +
            "WHERE p.available = true " +
            "AND (p.store_id = #{storeId} " +
            "     OR (p.store_id = 0 AND NOT EXISTS " +
            "         (SELECT 1 FROM menu_item q WHERE q.store_id = #{storeId} AND q.code = p.code))) " +
            "ORDER BY p.category_code, p.id")
    List<MenuItemPO> selectByStoreAvailable(@Param("storeId") Long storeId);

    /** 按店查全部商品（商家菜单管理，含下架）：店铺专属 + 未被店铺覆盖的全局品 */
    @Select("SELECT * FROM menu_item p " +
            "WHERE p.store_id = #{storeId} " +
            "   OR (p.store_id = 0 AND NOT EXISTS " +
            "       (SELECT 1 FROM menu_item q WHERE q.store_id = #{storeId} AND q.code = p.code)) " +
            "ORDER BY p.category_code, p.id")
    List<MenuItemPO> selectByStore(@Param("storeId") Long storeId);

    @Select("SELECT * FROM menu_item WHERE category_code = #{categoryCode}")
    List<MenuItemPO> selectByCategory(@Param("categoryCode") String categoryCode);

    /**
     * 按店查可用凑单品（topup=1 且上架）：店铺专属 + 未被店铺覆盖的全局品，
     * 最低可买价（三档规格价与基础价取最小）不超过 maxPrice，按最低价升序（凑单先推便宜的）
     */
    @Select("SELECT * FROM menu_item p " +
            "WHERE p.available = true AND p.topup = 1 " +
            "AND (p.store_id = #{storeId} " +
            "     OR (p.store_id = 0 AND NOT EXISTS " +
            "         (SELECT 1 FROM menu_item q WHERE q.store_id = #{storeId} AND q.code = p.code))) " +
            "AND LEAST(COALESCE(p.price_small, p.base_price), COALESCE(p.price_medium, p.base_price), " +
            "          COALESCE(p.price_large, p.base_price), p.base_price) <= #{maxPrice} " +
            "ORDER BY LEAST(COALESCE(p.price_small, p.base_price), COALESCE(p.price_medium, p.base_price), " +
            "               COALESCE(p.price_large, p.base_price), p.base_price), p.id")
    List<MenuItemPO> selectTopupByStoreAvailable(@Param("storeId") Long storeId, @Param("maxPrice") Double maxPrice);

    @Select("<script>" +
            "SELECT * FROM menu_item WHERE code IN " +
            "<foreach collection='codes' item='code' open='(' separator=',' close=')'>#{code}</foreach>" +
            "</script>")
    List<MenuItemPO> selectByCodes(@Param("codes") List<String> codes);
}
