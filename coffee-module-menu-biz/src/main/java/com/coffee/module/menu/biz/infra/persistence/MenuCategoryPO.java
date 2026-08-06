package com.coffee.module.menu.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 商品分类持久化对象
 */
@Data
@TableName("menu_category")
public class MenuCategoryPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 分类编码（唯一） */
    private String code;
    /** 分类名称 */
    private String name;
    /** 图标 */
    private String icon;
    /** 排序（共享类目 1-5，自定义类目为 0） */
    private Integer sortOrder;
    /** 归属：0=共享类目（所有店可见），N=该商家创建的自定义类目（仅本店可见） */
    private Long storeId;
}
