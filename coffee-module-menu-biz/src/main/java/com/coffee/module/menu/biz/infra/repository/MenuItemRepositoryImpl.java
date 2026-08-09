package com.coffee.module.menu.biz.infra.repository;

import com.coffee.module.menu.biz.domain.MenuItem;
import com.coffee.module.menu.biz.domain.repository.MenuItemRepository;
import com.coffee.module.menu.biz.infra.persistence.MenuItemMapper;
import com.coffee.module.menu.biz.infra.persistence.MenuItemPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 产品仓储实现
 */
@Repository
public class MenuItemRepositoryImpl implements MenuItemRepository {

    private final MenuItemMapper productMapper;

    public MenuItemRepositoryImpl(MenuItemMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    public MenuItem findByCodeAndStore(Long storeId, String code) {
        // 店铺专属品优先，无则回退全局品（store_id = 0）
        MenuItemPO po = productMapper.selectByCodeAndStore(storeId, code);
        if (po == null) {
            po = productMapper.selectGlobalByCode(code);
        }
        return po != null ? toDomain(po) : null;
    }

    @Override
    public List<MenuItem> findByStoreAvailable(Long storeId) {
        return productMapper.selectByStoreAvailable(storeId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<MenuItem> findByStore(Long storeId) {
        return productMapper.selectByStore(storeId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public MenuItem findById(Long id) {
        MenuItemPO po = productMapper.selectById(id);
        return po != null ? toDomain(po) : null;
    }

    @Override
    public MenuItem save(MenuItem product) {
        MenuItemPO po = toPO(product);
        productMapper.insert(po);
        product.setId(po.getId());
        return product;
    }

    @Override
    public MenuItem update(MenuItem product) {
        productMapper.updateById(toPO(product));
        return product;
    }

    @Override
    public List<MenuItem> findByCategory(String categoryCode) {
        return productMapper.selectByCategory(categoryCode).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<MenuItem> findByCodeIn(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return productMapper.selectByCodes(codes).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<MenuItem> findTopupByStoreAvailable(Long storeId, double maxPrice) {
        return productMapper.selectTopupByStoreAvailable(storeId, maxPrice).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private MenuItem toDomain(MenuItemPO po) {
        MenuItem product = new MenuItem();
        product.setId(po.getId());
        product.setStoreId(po.getStoreId());
        product.setCode(po.getCode());
        product.setName(po.getName());
        product.setCategoryId(po.getCategoryId());
        product.setCategoryCode(po.getCategoryCode());
        product.setBasePrice(po.getBasePrice());
        product.setPriceSmall(po.getPriceSmall());
        product.setPriceMedium(po.getPriceMedium());
        product.setPriceLarge(po.getPriceLarge());
        product.setDescription(po.getDescription());
        product.setImageUrl(po.getImageUrl());
        product.setTemperature(po.getTemperature());
        product.setAvailable(po.getAvailable());
        product.setTopup(po.getTopup());
        return product;
    }

    private MenuItemPO toPO(MenuItem product) {
        MenuItemPO po = new MenuItemPO();
        po.setId(product.getId());
        po.setStoreId(product.getStoreId());
        po.setCode(product.getCode());
        po.setName(product.getName());
        po.setCategoryId(product.getCategoryId());
        po.setCategoryCode(product.getCategoryCode());
        po.setBasePrice(product.getBasePrice());
        po.setPriceSmall(product.getPriceSmall());
        po.setPriceMedium(product.getPriceMedium());
        po.setPriceLarge(product.getPriceLarge());
        po.setDescription(product.getDescription());
        po.setImageUrl(product.getImageUrl());
        po.setTemperature(product.getTemperature());
        po.setAvailable(product.getAvailable());
        po.setTopup(product.getTopup());
        return po;
    }
}
