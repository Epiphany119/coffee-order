package com.coffee.module.menu.biz.domain.repository;

import com.coffee.module.menu.biz.infra.persistence.MenuCategoryPO;

import java.util.List;

/**
 * 商品分类仓储接口（类目无业务逻辑，直接以 PO 交互）
 */
public interface MenuCategoryRepository {
    /** 店铺可见类目：共享类目 + 该店自定义类目 */
    List<MenuCategoryPO> listByStore(Long storeId);

    /** 按编码查类目 */
    MenuCategoryPO findByCode(String code);

    /** 新增类目（id 自增回填） */
    MenuCategoryPO save(MenuCategoryPO category);
}
