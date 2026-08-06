package com.coffee.module.menu.biz.infra.repository;

import com.coffee.module.menu.biz.domain.repository.MenuCategoryRepository;
import com.coffee.module.menu.biz.infra.persistence.MenuCategoryMapper;
import com.coffee.module.menu.biz.infra.persistence.MenuCategoryPO;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 商品分类仓储实现
 */
@Repository
public class MenuCategoryRepositoryImpl implements MenuCategoryRepository {

    private final MenuCategoryMapper categoryMapper;

    public MenuCategoryRepositoryImpl(MenuCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<MenuCategoryPO> listByStore(Long storeId) {
        return categoryMapper.selectByStore(storeId);
    }

    @Override
    public MenuCategoryPO findByCode(String code) {
        return categoryMapper.selectByCode(code);
    }

    @Override
    public MenuCategoryPO save(MenuCategoryPO category) {
        categoryMapper.insert(category);
        return category;
    }
}
