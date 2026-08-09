package com.coffee.module.menu.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 商品持久化对象（菜单项）
 */
@Data
@TableName("menu_item")
public class MenuItemPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 状态码（归属）：0=初始化的全局商品（所有店共享），N=该店专属商品（商家新增/覆盖品），菜单查询按此匹配 */
    private Long storeId;
    private String code;
    private String name;
    /** 分类外键（menu_category.id，与 categoryCode 保持一致） */
    private Long categoryId;
    private String categoryCode;
    /** 基础价（规格价缺失时的兜底价） */
    private BigDecimal basePrice;
    /** 规格定价：小份/基础款（空=回退 basePrice） */
    private Double priceSmall;
    /** 规格定价：中份/中等款（空=回退 basePrice） */
    private Double priceMedium;
    /** 规格定价：大份/加大款（空=回退 basePrice） */
    private Double priceLarge;
    private String description;
    private String imageUrl;
    private String temperature;
    private Boolean available;
    /** 凑单标记：1=凑单推荐品（配料/小料/小饮品/试吃品，供购物袋凑单弹窗推荐） */
    private Integer topup;
}
