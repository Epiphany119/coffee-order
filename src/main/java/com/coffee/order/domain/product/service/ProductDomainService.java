package com.coffee.order.domain.product.service;

import com.coffee.order.domain.product.entity.Product;
import com.coffee.order.domain.product.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 产品领域服务
 */
@Service
public class ProductDomainService {

    private final ProductRepository productRepository;

    public ProductDomainService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product findProductByCode(String code) {
        return productRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在: " + code));
    }

    public List<Product> getAllAvailableProducts() {
        return productRepository.findAllAvailable();
    }

    public double calculatePrice(Product product, String size, List<String> condiments) {
        double basePrice = product.getBasePriceAsDouble();
        double sizeExtra = getSizeExtraPrice(size, product.getCategoryCode());
        double condimentExtra = getCondimentExtraPrice(condiments);
        return Math.round((basePrice + sizeExtra + condimentExtra) * 100.0) / 100.0;
    }

    public String getDisplayName(Product product, String size, List<String> condiments) {
        String name = product.getName();
        String sizeLabel = getSizeLabel(size);
        String condimentSuffix = condiments != null && !condiments.isEmpty()
                ? " + " + String.join(", ", condiments)
                : "";
        return sizeLabel + " " + name + condimentSuffix;
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
}
