package com.coffee.module.menu.biz.domain.repository;

import com.coffee.module.menu.biz.domain.MenuItem;
import java.util.List;

/**
 * 产品仓储接口
 */
public interface MenuItemRepository {
    MenuItem findByCodeAndStore(Long storeId, String code);
    List<MenuItem> findByStoreAvailable(Long storeId);
    /** 按店查全部（含下架，商家菜单管理） */
    List<MenuItem> findByStore(Long storeId);
    MenuItem findById(Long id);
    MenuItem save(MenuItem product);
    MenuItem update(MenuItem product);
    List<MenuItem> findByCategory(String categoryCode);
    List<MenuItem> findByCodeIn(List<String> codes);
    /** 按店查可用凑单品（topup=1，最低可买价 ≤ maxPrice，按最低价升序） */
    List<MenuItem> findTopupByStoreAvailable(Long storeId, double maxPrice);
}
