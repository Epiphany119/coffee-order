package com.coffee.module.store.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 店铺 Mapper
 */
public interface StoreMapper extends BaseMapper<StorePO> {

    /** 按编码查询 */
    @Select("SELECT * FROM store WHERE code = #{code} LIMIT 1")
    StorePO selectByCode(@Param("code") String code);

    /** 商家名下店铺，按 id 升序 */
    @Select("SELECT * FROM store WHERE merchant_id = #{merchantId} ORDER BY id ASC")
    List<StorePO> selectByMerchantId(@Param("merchantId") Long merchantId);

    /** 可入驻店铺（未绑定商家），按 id 升序 */
    @Select("SELECT * FROM store WHERE merchant_id IS NULL ORDER BY id ASC")
    List<StorePO> selectAvailable();
}
