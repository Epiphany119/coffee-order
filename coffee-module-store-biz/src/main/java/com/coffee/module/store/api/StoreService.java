package com.coffee.module.store.api;

import com.coffee.module.store.api.dto.StoreRequest;
import com.coffee.module.store.api.dto.StoreResponse;

import java.util.List;

/**
 * 店铺服务接口
 */
public interface StoreService {

    /**
     * 创建店铺
     */
    StoreResponse createStore(StoreRequest request);

    /**
     * 全部店铺列表（按 id 升序，用户端选店 / 商家端管理用）
     */
    List<StoreResponse> listStores();

    /**
     * 可入驻店铺列表（merchant_id 为空，商家入驻选择）
     */
    List<StoreResponse> listAvailableStores();

    /**
     * 营业中店铺列表（status = OPEN，用户端左上角选店）
     */
    List<StoreResponse> listOpenStores();

    /**
     * 店铺详情
     */
    StoreResponse getStore(Long storeId);

    /**
     * 更新店铺信息（code 不可修改）
     */
    StoreResponse updateStore(Long storeId, StoreRequest request);

    /**
     * 删除店铺
     */
    void deleteStore(Long storeId);

    /**
     * 绑定商家到店铺（入驻）。一商一店：店铺已被入驻或商家已入驻其他店时拒绝
     */
    StoreResponse bindMerchant(Long storeId, Long merchantId);

    /**
     * 商家名下店铺列表（登录后"我的店铺"）
     */
    List<StoreResponse> listByMerchant(Long merchantId);
}
