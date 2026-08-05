package com.coffee.module.product.biz.application.service;

import com.coffee.module.product.api.FavoriteService;
import com.coffee.module.product.api.dto.ProductDTO;
import com.coffee.module.product.biz.domain.Product;
import com.coffee.module.product.biz.domain.repository.ProductRepository;
import com.coffee.module.product.biz.infra.persistence.UserFavoriteMapper;
import com.coffee.module.product.biz.infra.persistence.UserFavoritePO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收藏应用服务
 */
@Service
public class FavoriteServiceImpl implements FavoriteService {

    private final UserFavoriteMapper userFavoriteMapper;
    private final ProductRepository productRepository;

    public FavoriteServiceImpl(UserFavoriteMapper userFavoriteMapper, ProductRepository productRepository) {
        this.userFavoriteMapper = userFavoriteMapper;
        this.productRepository = productRepository;
    }

    @Override
    public List<ProductDTO> getFavorites(Long userId) {
        List<UserFavoritePO> favorites = userFavoriteMapper.selectByUserId(userId);
        if (favorites.isEmpty()) {
            return List.of();
        }
        List<String> codes = favorites.stream()
                .map(UserFavoritePO::getProductCode)
                .collect(Collectors.toList());
        Map<String, Product> byCode = productRepository.findByCodeIn(codes).stream()
                .collect(Collectors.toMap(Product::getCode, Function.identity(), (a, b) -> a));
        return favorites.stream()
                .map(f -> byCode.get(f.getProductCode()))
                .filter(p -> p != null)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public void addFavorite(Long userId, String productCode) {
        if (userFavoriteMapper.countByUserIdAndCode(userId, productCode) > 0) {
            return;
        }
        UserFavoritePO po = new UserFavoritePO();
        po.setUserId(userId);
        po.setProductCode(productCode);
        po.setCreatedAt(LocalDateTime.now());
        userFavoriteMapper.insert(po);
    }

    @Override
    public void removeFavorite(Long userId, String productCode) {
        userFavoriteMapper.deleteByUserIdAndCode(userId, productCode);
    }

    private ProductDTO toDTO(Product product) {
        ProductDTO dto = new ProductDTO();
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
