package com.coffee.web.controller;

import com.coffee.module.menu.api.MenuService;
import com.coffee.module.menu.api.dto.MenuCategoryDTO;
import com.coffee.module.menu.api.dto.MenuCategoryRequest;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.coffee.module.menu.api.dto.MenuItemRequest;
import com.coffee.module.menu.biz.infra.storage.LocalImageStorage;
import com.coffee.module.store.api.StoreService;
import com.coffee.module.store.api.dto.StoreRequest;
import com.coffee.module.store.api.dto.StoreResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 店铺控制器（店铺管理 / 用户端选店 / 商家菜单管理）
 */
@RestController
@RequestMapping("/api/store")
public class StoreController {

    private final StoreService storeService;
    private final MenuService productService;
    private final LocalImageStorage imageStorage;

    public StoreController(StoreService storeService, MenuService productService,
                           LocalImageStorage imageStorage) {
        this.storeService = storeService;
        this.productService = productService;
        this.imageStorage = imageStorage;
    }

    /** 创建店铺（商家建店 / 管理端） */
    @PostMapping
    public StoreResponse create(@RequestBody StoreRequest request) {
        return storeService.createStore(request);
    }

    /** 全部店铺列表（用户端选店） */
    @GetMapping("/list")
    public List<StoreResponse> list() {
        return storeService.listStores();
    }

    /** 可入驻店铺列表（商家入驻选择：21 家种子店中未入驻的） */
    @GetMapping("/available")
    public List<StoreResponse> available() {
        return storeService.listAvailableStores();
    }

    /** 营业中店铺列表（用户端左上角选店） */
    @GetMapping("/open")
    public List<StoreResponse> open() {
        return storeService.listOpenStores();
    }

    /** 店铺详情 */
    @GetMapping("/{id}")
    public StoreResponse detail(@PathVariable("id") Long id) {
        return storeService.getStore(id);
    }

    /** 更新店铺（店名/地址/电话/营业时间/状态） */
    @PutMapping("/{id}")
    public StoreResponse update(@PathVariable("id") Long id, @RequestBody StoreRequest request) {
        return storeService.updateStore(id, request);
    }

    /** 删除店铺 */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        storeService.deleteStore(id);
    }

    /** 绑定商家到店铺 */
    @PostMapping("/{id}/bind")
    public StoreResponse bind(@PathVariable("id") Long id,
                              @RequestParam("merchantId") Long merchantId) {
        return storeService.bindMerchant(id, merchantId);
    }

    /** 商家菜单：按店查全部商品（含下架） */
    @GetMapping("/{storeId}/menu")
    public List<MenuItemDTO> storeMenu(@PathVariable("storeId") Long storeId) {
        return productService.listByStore(storeId);
    }

    /** 店铺可见类目：共享类目 + 该店自定义类目 */
    @GetMapping("/{storeId}/categories")
    public List<MenuCategoryDTO> storeCategories(@PathVariable("storeId") Long storeId) {
        return productService.listCategories(storeId);
    }

    /** 商家创建自定义类目（仅本店可见） */
    @PostMapping("/{storeId}/category")
    public MenuCategoryDTO createCategory(@PathVariable("storeId") Long storeId,
                                          @RequestBody MenuCategoryRequest request) {
        return productService.createCategory(storeId, request);
    }

    /** 商家菜单：新增商品 */
    @PostMapping("/{storeId}/menu")
    public MenuItemDTO createMenu(@PathVariable("storeId") Long storeId,
                                 @RequestBody MenuItemRequest request) {
        return productService.createForStore(storeId, request);
    }

    /** 商家菜单：上传商品图片，返回可访问 URL（本地磁盘 coffee-uploads 目录） */
    @PostMapping("/{storeId}/menu/image")
    public Map<String, String> uploadMenuImage(@PathVariable("storeId") Long storeId,
                                               @RequestParam("file") MultipartFile file) {
        return Map.of("url", imageStorage.store(storeId, file));
    }

    /** 商家菜单：更新商品（改价/描述/上下架） */
    @PutMapping("/{storeId}/menu/{productId}")
    public MenuItemDTO updateMenu(@PathVariable("storeId") Long storeId,
                                 @PathVariable("productId") Long productId,
                                 @RequestBody MenuItemRequest request) {
        return productService.updateProduct(storeId, productId, request);
    }
}
