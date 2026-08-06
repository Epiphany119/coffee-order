package com.coffee.module.menu.biz.application.service;

import com.coffee.module.menu.api.FavoriteService;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.coffee.module.menu.biz.domain.MenuItem;
import com.coffee.module.menu.biz.domain.repository.MenuItemRepository;
import com.coffee.module.menu.biz.infra.persistence.UserFavoriteMapper;
import com.coffee.module.menu.biz.infra.persistence.UserFavoritePO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收藏应用服务（登录用户按 userId、游客按 guestId 隔离存储）
 */
@Service
public class FavoriteServiceImpl implements FavoriteService {

    private final UserFavoriteMapper userFavoriteMapper;
    private final MenuItemRepository productRepository;

    public FavoriteServiceImpl(UserFavoriteMapper userFavoriteMapper, MenuItemRepository productRepository) {
        this.userFavoriteMapper = userFavoriteMapper;
        this.productRepository = productRepository;
    }

    @Override
    public List<MenuItemDTO> getFavorites(Long userId, String guestId) {
        List<UserFavoritePO> favorites;
        if (userId != null) {
            favorites = userFavoriteMapper.selectByUserId(userId);
        } else if (guestId != null && !guestId.isBlank()) {
            favorites = userFavoriteMapper.selectByGuestId(guestId);
        } else {
            return List.of();
        }
        if (favorites.isEmpty()) {
            return List.of();
        }
        List<String> codes = favorites.stream()
                .map(UserFavoritePO::getProductCode)
                .collect(Collectors.toList());
        Map<String, MenuItem> byCode = productRepository.findByCodeIn(codes).stream()
                .collect(Collectors.toMap(MenuItem::getCode, Function.identity(), (a, b) -> a));
        return favorites.stream()
                .map(f -> byCode.get(f.getProductCode()))
                .filter(p -> p != null)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public void addFavorite(Long userId, String guestId, String productCode) {
        if (userId != null) {
            if (userFavoriteMapper.countByUserIdAndCode(userId, productCode) > 0) {
                return;
            }
        } else if (guestId != null && !guestId.isBlank()) {
            if (userFavoriteMapper.countByGuestIdAndCode(guestId, productCode) > 0) {
                return;
            }
        } else {
            return;
        }
        UserFavoritePO po = new UserFavoritePO();
        po.setUserId(userId);
        po.setGuestId(guestId);
        po.setProductCode(productCode);
        po.setCreatedAt(LocalDateTime.now());
        userFavoriteMapper.insert(po);
    }

    @Override
    public void removeFavorite(Long userId, String guestId, String productCode) {
        if (userId != null) {
            userFavoriteMapper.deleteByUserIdAndCode(userId, productCode);
        } else if (guestId != null && !guestId.isBlank()) {
            userFavoriteMapper.deleteByGuestIdAndCode(guestId, productCode);
        }
    }

    @Override
    @Transactional
    public void mergeGuestToUser(Long userId, String guestId) {
        if (userId == null || guestId == null || guestId.isBlank()) {
            return;
        }
        List<UserFavoritePO> guestFavorites = userFavoriteMapper.selectByGuestId(guestId);
        for (UserFavoritePO f : guestFavorites) {
            if (userFavoriteMapper.countByUserIdAndCode(userId, f.getProductCode()) == 0) {
                UserFavoritePO po = new UserFavoritePO();
                po.setUserId(userId);
                po.setProductCode(f.getProductCode());
                po.setCreatedAt(LocalDateTime.now());
                userFavoriteMapper.insert(po);
            }
        }
        userFavoriteMapper.deleteByGuestId(guestId);
    }

    private MenuItemDTO toDTO(MenuItem product) {
        MenuItemDTO dto = new MenuItemDTO();
        dto.setId(product.getId());
        dto.setCode(product.getCode());
        dto.setName(product.getName());
        dto.setCategoryCode(product.getCategoryCode());
        dto.setBasePrice(product.getBasePriceAsDouble());
        dto.setDescription(product.getDescription());
        dto.setImageUrl(product.getImageUrl());
        dto.setTemperature(product.getTemperature());
        dto.setAvailable(product.getAvailable());
        return dto;
    }
}
