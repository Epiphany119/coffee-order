package com.coffee.module.menu.api.dto;

import lombok.Data;

/**
 * 商品分类 DTO
 */
@Data
public class MenuCategoryDTO {
    private Long id;
    /** 分类编码（唯一） */
    private String code;
    /** 分类名称 */
    private String name;
    /** 图标 */
    private String icon;
    /** 归属：0=共享类目（所有店可见），N=该商家创建的自定义类目（仅本店可见） */
    private Long storeId;
}
