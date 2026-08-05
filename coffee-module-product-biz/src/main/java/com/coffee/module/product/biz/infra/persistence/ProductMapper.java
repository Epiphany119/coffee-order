package com.coffee.module.product.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 产品 Mapper
 */
@Mapper
public interface ProductMapper extends BaseMapper<ProductPO> {

    @Select("SELECT * FROM product WHERE code = #{code}")
    ProductPO selectByCode(@Param("code") String code);

    @Select("SELECT * FROM product WHERE available = true ORDER BY category_code, id")
    List<ProductPO> selectAllAvailable();

    @Select("SELECT * FROM product WHERE category_code = #{categoryCode}")
    List<ProductPO> selectByCategory(@Param("categoryCode") String categoryCode);

    @Select("<script>" +
            "SELECT * FROM product WHERE code IN " +
            "<foreach collection='codes' item='code' open='(' separator=',' close=')'>#{code}</foreach>" +
            "</script>")
    List<ProductPO> selectByCodes(@Param("codes") List<String> codes);
}
