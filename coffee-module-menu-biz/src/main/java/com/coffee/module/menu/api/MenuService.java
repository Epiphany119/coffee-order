package com.coffee.module.menu.api;

import com.coffee.module.menu.api.dto.MenuCategoryDTO;
import com.coffee.module.menu.api.dto.MenuCategoryRequest;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.coffee.module.menu.api.dto.MenuItemRequest;

import java.util.List;

/**
 * 商品服务 API（菜单管理 / 计价 / 定制）
 *
 * ⚠️ 定制规格规则集中配置区（比例规则后续由业务确定，只改这里）：
 * - 定制单位按分类：coffee/tea/ice → ml（毫升），dessert/food → g（克）
 * - 定制价 = 中等款定价 ×（定制量 ÷ 基准量），取整到分
 * - 基准量：饮品 CUSTOM_BASE_ML ml / 甜点轻食 CUSTOM_BASE_G g（视为"中等款"标准量）
 */
public interface MenuService {

    /** 饮品定制基准容量（ml）：中等款标准量 */
    double CUSTOM_BASE_ML = 300;
    /** 甜点/轻食定制基准克重（g）：中等款标准量 */
    double CUSTOM_BASE_G = 100;

    /**
     * 根据店铺+编码获取商品
     */
    MenuItemDTO getProductByCode(Long storeId, String code);

    /**
     * 获取店铺可用商品（用户端菜单，下架不可见）
     */
    List<MenuItemDTO> getAllProducts(Long storeId);

    /**
     * 按店查全部商品（商家菜单管理，含下架）
     */
    List<MenuItemDTO> listByStore(Long storeId);

    /**
     * 凑单推荐：该店可用凑单品（topup=1 且上架），最低可买价 ≤ maxPrice，按最低价升序
     *
     * @param maxPrice 还差金额（购物袋金额距最近满减门槛的差额），仅推价格不超过它的凑单品
     */
    List<MenuItemDTO> listTopupProducts(Long storeId, double maxPrice);

    /**
     * 店铺可见类目：共享类目 + 该店自定义类目（仅本店可见）
     */
    List<MenuCategoryDTO> listCategories(Long storeId);

    /**
     * 商家创建自定义类目（仅本店可见，编码自动生成全局唯一）
     */
    MenuCategoryDTO createCategory(Long storeId, MenuCategoryRequest request);

    /**
     * 为店铺新增商品
     */
    MenuItemDTO createForStore(Long storeId, MenuItemRequest request);

    /**
     * 更新店铺商品（改价/规格定价/描述/上下架）
     */
    MenuItemDTO updateProduct(Long storeId, Long productId, MenuItemRequest request);

    /**
     * 计算商品总价（规格定价优先，缺失回退 basePrice；CUSTOM 规格按定制量比例计价）
     *
     * @param customSize 定制尺寸（仅 size=CUSTOM 时使用，如 "300"），为空按基准量计价
     */
    double calculatePrice(Long storeId, String productCode, String size, String customSize, List<String> condiments);

    /**
     * 获取商品展示名称（按店铺）
     *
     * @param customSize 定制尺寸（仅 size=CUSTOM 时使用，如 "300"）
     */
    String getDisplayName(Long storeId, String productCode, String size, String customSize, List<String> condiments);

    /**
     * 定制规格单位：coffee/tea/ice → ml，dessert/food → g
     */
    String customUnit(String categoryCode);
}
