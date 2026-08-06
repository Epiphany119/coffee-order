package com.coffee.module.menu.biz.domain.service;

import com.coffee.module.menu.biz.domain.MenuItem;
import com.coffee.module.menu.biz.domain.repository.MenuCategoryRepository;
import com.coffee.module.menu.biz.domain.repository.MenuItemRepository;
import com.coffee.module.menu.api.MenuService;
import com.coffee.module.menu.api.dto.MenuCategoryDTO;
import com.coffee.module.menu.api.dto.MenuCategoryRequest;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.coffee.module.menu.api.dto.MenuItemRequest;
import com.coffee.module.menu.biz.infra.persistence.MenuCategoryPO;
import com.coffee.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 产品领域服务
 */
@Service
public class MenuDomainService implements MenuService {

    private final MenuItemRepository productRepository;
    private final MenuCategoryRepository categoryRepository;

    public MenuDomainService(MenuItemRepository productRepository,
                             MenuCategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public MenuItemDTO getProductByCode(Long storeId, String code) {
        MenuItem product = productRepository.findByCodeAndStore(storeId, code);
        if (product == null) {
            throw new ServiceException(404, "产品不存在: " + code);
        }
        return toDTO(product);
    }

    @Override
    public List<MenuItemDTO> getAllProducts(Long storeId) {
        return productRepository.findByStoreAvailable(storeId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public List<MenuItemDTO> listByStore(Long storeId) {
        return productRepository.findByStore(storeId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public List<MenuCategoryDTO> listCategories(Long storeId) {
        return categoryRepository.listByStore(storeId).stream()
                .map(this::toCategoryDTO)
                .toList();
    }

    @Override
    public MenuCategoryDTO createCategory(Long storeId, MenuCategoryRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ServiceException(400, "类目名称不能为空");
        }
        MenuCategoryPO category = new MenuCategoryPO();
        // 归属：N=该商家创建的自定义类目（仅本店可见）
        category.setStoreId(storeId);
        category.setName(request.getName().trim());
        category.setIcon(request.getIcon() != null && !request.getIcon().isBlank()
                ? request.getIcon().trim() : "🏷️");
        category.setSortOrder(0);
        // 自定义类目编码：cus + 毫秒时间戳，保证 code 全局唯一（UNIQUE 约束）
        category.setCode("cus" + System.currentTimeMillis());
        return toCategoryDTO(categoryRepository.save(category));
    }

    @Override
    public MenuItemDTO createForStore(Long storeId, MenuItemRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new ServiceException(400, "商品编码不能为空");
        }
        // 冲突校验覆盖全局品：与全局初始品同 code 时拒绝（改全局品请走编辑接口，会自动生成本店专属品）
        if (productRepository.findByCodeAndStore(storeId, request.getCode()) != null) {
            throw new ServiceException(400, "该店已存在此编码的商品: " + request.getCode());
        }
        MenuItem product = new MenuItem();
        // store_id 即状态码：商家新增的商品为该店专属品（状态码 = storeId）
        product.setStoreId(storeId);
        product.setCode(request.getCode());
        product.setName(request.getName());
        product.setCategoryCode(request.getCategoryCode());
        product.setCategoryId(resolveCategoryId(request.getCategoryId(), request.getCategoryCode()));
        product.setBasePrice(java.math.BigDecimal.valueOf(
                request.getBasePrice() != null ? request.getBasePrice() : 0));
        product.setPriceSmall(request.getPriceSmall());
        product.setPriceMedium(request.getPriceMedium());
        product.setPriceLarge(request.getPriceLarge());
        product.setDescription(request.getDescription());
        product.setImageUrl(request.getImageUrl());
        product.setTemperature(request.getTemperature());
        product.setAvailable(request.getAvailable() != null ? request.getAvailable() : true);
        return toDTO(productRepository.save(product));
    }

    @Override
    public MenuItemDTO updateProduct(Long storeId, Long productId, MenuItemRequest request) {
        MenuItem product = productRepository.findById(productId);
        if (product == null) {
            throw new ServiceException(404, "商品不存在: " + productId);
        }
        // 全局初始品（store_id=0）：懒复制为该店专属品后再更新，修改只影响本店，其他店仍用全局品
        if (product.getStoreId() == null || product.getStoreId() == 0L) {
            MenuItem copy = new MenuItem();
            copy.setStoreId(storeId);
            copy.setCode(product.getCode());
            copy.setName(product.getName());
            copy.setCategoryId(product.getCategoryId());
            copy.setCategoryCode(product.getCategoryCode());
            copy.setBasePrice(product.getBasePrice());
            copy.setPriceSmall(product.getPriceSmall());
            copy.setPriceMedium(product.getPriceMedium());
            copy.setPriceLarge(product.getPriceLarge());
            copy.setDescription(product.getDescription());
            copy.setImageUrl(product.getImageUrl());
            copy.setTemperature(product.getTemperature());
            copy.setAvailable(product.getAvailable());
            product = productRepository.save(copy);
        } else if (!storeId.equals(product.getStoreId())) {
            throw new ServiceException(404, "商品不存在: " + productId);
        }
        if (request.getCode() != null && !request.getCode().isBlank() && !request.getCode().equals(product.getCode())) {
            if (productRepository.findByCodeAndStore(storeId, request.getCode()) != null) {
                throw new ServiceException(400, "该店已存在此编码的商品: " + request.getCode());
            }
            product.setCode(request.getCode());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            product.setName(request.getName());
        }
        if (request.getCategoryCode() != null && !request.getCategoryCode().isBlank()) {
            product.setCategoryCode(request.getCategoryCode());
        }
        if (request.getCategoryId() != null) {
            product.setCategoryId(request.getCategoryId());
        } else if (request.getCategoryCode() != null && !request.getCategoryCode().isBlank()) {
            // 兜底：未传 categoryId 时按 categoryCode 反查（兼容旧调用方，保证 category_id 不落 NULL）
            Long categoryId = resolveCategoryId(null, request.getCategoryCode());
            if (categoryId != null) {
                product.setCategoryId(categoryId);
            }
        }
        if (request.getBasePrice() != null) {
            product.setBasePrice(java.math.BigDecimal.valueOf(request.getBasePrice()));
        }
        // 规格定价：显式传 null 表示不修改；传值则覆盖（清空请传 0）
        if (request.getPriceSmall() != null) {
            product.setPriceSmall(request.getPriceSmall());
        }
        if (request.getPriceMedium() != null) {
            product.setPriceMedium(request.getPriceMedium());
        }
        if (request.getPriceLarge() != null) {
            product.setPriceLarge(request.getPriceLarge());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getImageUrl() != null) {
            product.setImageUrl(request.getImageUrl());
        }
        if (request.getTemperature() != null) {
            product.setTemperature(request.getTemperature());
        }
        if (request.getAvailable() != null) {
            product.setAvailable(request.getAvailable());
        }
        return toDTO(productRepository.update(product));
    }

    @Override
    public double calculatePrice(Long storeId, String productCode, String size, String customSize, List<String> condiments) {
        MenuItem product = productRepository.findByCodeAndStore(storeId, productCode);
        if (product == null) {
            throw new ServiceException(404, "产品不存在: " + productCode);
        }
        return calculatePriceInternal(product, size, customSize, condiments);
    }

    @Override
    public String getDisplayName(Long storeId, String productCode, String size, String customSize, List<String> condiments) {
        MenuItem product = productRepository.findByCodeAndStore(storeId, productCode);
        if (product == null) {
            throw new ServiceException(404, "产品不存在: " + productCode);
        }
        return getDisplayNameInternal(product, size, customSize, condiments);
    }

    @Override
    public String customUnit(String categoryCode) {
        return isLiquid(categoryCode) ? "ml" : "g";
    }

    /** 计价：规格定价优先（缺失回退 basePrice）；CUSTOM 规格按"中等款定价 × 定制量/基准量"比例计价 */
    public double calculatePriceInternal(MenuItem product, String size, String customSize, List<String> condiments) {
        double price;
        if ("CUSTOM".equals(size)) {
            price = calculateCustomPrice(product, customSize);
        } else {
            price = product.getSizePrice(size);
        }
        double condimentExtra = getCondimentExtraPrice(condiments);
        return com.coffee.common.core.util.MoneyUtils.round2(price + condimentExtra);
    }

    /** 定制规格计价：定制价 = 中等款定价 ×（定制量 ÷ 基准量），基准量按分类（饮品 ml / 甜点轻食 g） */
    private double calculateCustomPrice(MenuItem product, String customSize) {
        double amount = parseCustomSize(customSize);
        double mediumPrice = product.getSizePrice("MEDIUM");
        double base = isLiquid(product.getCategoryCode()) ? CUSTOM_BASE_ML : CUSTOM_BASE_G;
        return mediumPrice * amount / base;
    }

    /** 解析定制量：空/非法按基准量处理（比例 1.0），不允许 0 或负数 */
    private double parseCustomSize(String customSize) {
        if (customSize == null || customSize.isBlank()) return 0;
        try {
            double v = Double.parseDouble(customSize.trim());
            return v > 0 ? v : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getDisplayNameInternal(MenuItem product, String size, String customSize, List<String> condiments) {
        String sizeLabel = getSizeLabel(size, customSize, product.getCategoryCode());
        String condimentSuffix = condiments != null && !condiments.isEmpty()
                ? " + " + String.join(", ", condiments) : "";
        return sizeLabel + " " + product.getName() + condimentSuffix;
    }

    private boolean isLiquid(String categoryCode) {
        return "coffee".equals(categoryCode) || "tea".equals(categoryCode) || "ice".equals(categoryCode);
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

    /** 规格展示名：CUSTOM 显示"定制 450ml/g"（单位按分类） */
    private String getSizeLabel(String size, String customSize, String categoryCode) {
        if ("CUSTOM".equals(size)) {
            String amount = customSize != null ? customSize.trim() : "";
            if (amount.isEmpty()) return "定制";
            return "定制 " + amount + (isLiquid(categoryCode) ? "ml" : "g");
        }
        return switch (size) {
            case "SMALL" -> "小杯";
            case "LARGE" -> "大杯";
            default -> "中杯";
        };
    }

    /** 解析分类外键：优先用传入的 categoryId，否则按 categoryCode 反查类目表回填（保证 category_id 不落 NULL） */
    private Long resolveCategoryId(Long categoryId, String categoryCode) {
        if (categoryId != null) {
            return categoryId;
        }
        if (categoryCode == null || categoryCode.isBlank()) {
            return null;
        }
        MenuCategoryPO category = categoryRepository.findByCode(categoryCode);
        return category != null ? category.getId() : null;
    }

    private MenuCategoryDTO toCategoryDTO(MenuCategoryPO category) {
        MenuCategoryDTO dto = new MenuCategoryDTO();
        dto.setId(category.getId());
        dto.setCode(category.getCode());
        dto.setName(category.getName());
        dto.setIcon(category.getIcon());
        dto.setStoreId(category.getStoreId());
        return dto;
    }

    private MenuItemDTO toDTO(MenuItem product) {
        MenuItemDTO dto = new MenuItemDTO();
        dto.setId(product.getId());
        dto.setStoreId(product.getStoreId());
        dto.setCode(product.getCode());
        dto.setName(product.getName());
        dto.setCategoryId(product.getCategoryId());
        dto.setCategoryCode(product.getCategoryCode());
        dto.setBasePrice(product.getBasePriceAsDouble());
        dto.setPriceSmall(product.getPriceSmall());
        dto.setPriceMedium(product.getPriceMedium());
        dto.setPriceLarge(product.getPriceLarge());
        dto.setCustomUnit(customUnit(product.getCategoryCode()));
        dto.setDescription(product.getDescription());
        dto.setImageUrl(product.getImageUrl());
        dto.setTemperature(product.getTemperature());
        dto.setAvailable(product.getAvailable());
        return dto;
    }
}
