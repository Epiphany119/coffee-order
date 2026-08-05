package com.coffee.module.product.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 产品持久化对象
 */
@Data
@TableName("product")
public class ProductPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String categoryCode;
    private BigDecimal basePrice;
    private String description;
    private String imageUrl;
    private String temperature;
    private Boolean available;
}
