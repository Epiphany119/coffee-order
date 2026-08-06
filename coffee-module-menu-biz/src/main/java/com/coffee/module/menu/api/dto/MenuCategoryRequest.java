package com.coffee.module.menu.api.dto;

import lombok.Data;

/**
 * 商家自定义类目创建请求
 */
@Data
public class MenuCategoryRequest {
    /** 类目名称（必填） */
    private String name;
    /** 图标（可选，默认 🏷️） */
    private String icon;
}
