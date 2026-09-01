package com.coffee.module.store.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 商家 Mapper
 */
public interface MerchantMapper extends BaseMapper<MerchantPO> {

    /** 按商家编号查询（sj-开头） */
    @Select("SELECT * FROM merchant WHERE merchant_no = #{merchantNo} LIMIT 1")
    MerchantPO selectByMerchantNo(@Param("merchantNo") String merchantNo);

    /** 按登录账号查询（旧版 username，兼容存量数据） */
    @Select("SELECT * FROM merchant WHERE username = #{username} LIMIT 1")
    MerchantPO selectByUsername(@Param("username") String username);

    /** 资料页显式更新，允许清空可选字段。 */
    @Update("UPDATE merchant SET nickname = #{nickname}, phone = #{phone}, operator_name = #{operatorName}, "
            + "email = #{email}, business_license_no = #{businessLicenseNo}, other_info = #{otherInfo}, "
            + "updated_at = NOW() WHERE id = #{id}")
    void updateProfile(@Param("id") Long id,
                       @Param("nickname") String nickname,
                       @Param("phone") String phone,
                       @Param("operatorName") String operatorName,
                       @Param("email") String email,
                       @Param("businessLicenseNo") String businessLicenseNo,
                       @Param("otherInfo") String otherInfo);

    @Update("UPDATE merchant SET avatar_url = #{avatarUrl}, updated_at = NOW() WHERE id = #{id}")
    void updateAvatar(@Param("id") Long id, @Param("avatarUrl") String avatarUrl);

    @Update("UPDATE merchant SET business_license_url = #{licenseUrl}, updated_at = NOW() WHERE id = #{id}")
    void updateBusinessLicense(@Param("id") Long id, @Param("licenseUrl") String licenseUrl);
}
