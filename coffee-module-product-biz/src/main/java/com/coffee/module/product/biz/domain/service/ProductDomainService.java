package com.coffee.module.product.biz.domain.service;

import com.coffee.module.product.biz.domain.Product;
import com.coffee.module.product.biz.domain.repository.ProductRepository;
import com.coffee.module.product.api.ProductService;
import com.coffee.module.product.api.dto.ProductDTO;
import com.coffee.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 产品领域服务
 */
@Service
public class ProductDomainService implements ProductService {

    private final ProductRepository productRepository;

    public ProductDomainService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public ProductDTO getProductByCode(String code) {
        Product product = productRepository.findByCode(code);
        if (product == null) {
            throw new ServiceException(404, "产品不存在: " + code);
        }
        return toDTO(product);
    }

    @Override
    public List<ProductDTO> getAllProducts() {
        return productRepository.findAllAvailable().stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public double calculatePrice(String productCode, String size, List<String> condiments) {
        Product product = productRepository.findByCode(productCode);
        if (product == null) {
            throw new ServiceException(404, "产品不存在: " + productCode);
        }
        return calculatePriceInternal(product, size, condiments);
    }

    @Override
    public String getDisplayName(String productCode, String size, List<String> condiments) {
        Product product = productRepository.findByCode(productCode);
        if (product == null) {
            throw new ServiceException(404, "产品不存在: " + productCode);
        }
        return getDisplayNameInternal(product, size, condiments);
    }

    public double calculatePriceInternal(Product product, String size, List<String> condiments) {
        double basePrice = product.getBasePriceAsDouble();
        double sizeExtra = getSizeExtraPrice(size, product.getCategoryCode());
        double condimentExtra = getCondimentExtraPrice(condiments);
        return com.coffee.common.core.util.MoneyUtils.round2(basePrice + sizeExtra + condimentExtra);
    }

    public String getDisplayNameInternal(Product product, String size, List<String> condiments) {
        String sizeLabel = getSizeLabel(size);
        String condimentSuffix = condiments != null && !condiments.isEmpty()
                ? " + " + String.join(", ", condiments) : "";
        return sizeLabel + " " + product.getName() + condimentSuffix;
    }

    private double getSizeExtraPrice(String size, String categoryCode) {
        if ("dessert".equals(categoryCode) || "food".equals(categoryCode)) {
            return switch (size) {
                case "SMALL" -> 0;
                case "LARGE" -> 2;
                default -> 1;
            };
        }
        return switch (size) {
            case "SMALL" -> -2;
            case "LARGE" -> 3;
            default -> 0;
        };
    }

    private double getCondimentExtraPrice(List<String> condiments) {
        if (condiments == null || condiments.isEmpty()) return 0;
        double total = 0;
        for (String c : condiments) {
            total += switch (c) {
                case "珍珠", "波霸", "椰果", "芋圆" -> 0;
                case "芝士奶盖", "奶盖" -> 5;
                case "燕麦奶", "椰乳" -> 4;
                case "摩卡", "焦糖", "香草", "榛果" -> 3;
                case "冰淇淋", "芝士波波", "芋泥波波" -> 6;
                default -> 1;
            };
        }
        return total;
    }

    private String getSizeLabel(String size) {
        return switch (size) {
            case "SMALL" -> "小杯";
            case "LARGE" -> "大杯";
            default -> "中杯";
        };
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
