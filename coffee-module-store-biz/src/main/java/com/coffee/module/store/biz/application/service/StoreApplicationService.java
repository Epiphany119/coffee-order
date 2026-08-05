package com.coffee.module.store.biz.application.service;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.store.api.StoreService;
import com.coffee.module.store.api.dto.StoreRequest;
import com.coffee.module.store.api.dto.StoreResponse;
import com.coffee.module.store.api.dto.StoreStatus;
import com.coffee.module.store.biz.domain.Store;
import com.coffee.module.store.biz.domain.repository.MerchantRepository;
import com.coffee.module.store.biz.domain.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 店铺应用服务
 */
@Service
public class StoreApplicationService implements StoreService {

    private final StoreRepository storeRepository;
    private final MerchantRepository merchantRepository;

    public StoreApplicationService(StoreRepository storeRepository,
                                   MerchantRepository merchantRepository) {
        this.storeRepository = storeRepository;
        this.merchantRepository = merchantRepository;
    }

    @Override
    @Transactional
    public StoreResponse createStore(StoreRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new ServiceException(400, "店铺编码不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ServiceException(400, "店名不能为空");
        }
        if (storeRepository.existsByCode(request.getCode().trim())) {
            throw new ServiceException(400, "店铺编码已存在");
        }

        Store store = new Store();
        store.setCode(request.getCode().trim());
        store.setName(request.getName().trim());
        store.setAddress(request.getAddress());
        store.setPhone(request.getPhone());
        store.setBusinessHours(request.getBusinessHours());
        store.setStatus(request.getStatus() != null ? request.getStatus() : StoreStatus.OPEN);
        store.setMerchantId(request.getMerchantId());
        if (request.getMerchantId() != null) {
            requireMerchantNotJoined(request.getMerchantId());
        }
        storeRepository.save(store);
        return toResponse(store);
    }

    @Override
    public List<StoreResponse> listStores() {
        return storeRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<StoreResponse> listAvailableStores() {
        return storeRepository.findAvailable().stream().map(this::toResponse).toList();
    }

    @Override
    public StoreResponse getStore(Long storeId) {
        Store store = requireStore(storeId);
        return toResponse(store);
    }

    @Override
    @Transactional
    public StoreResponse updateStore(Long storeId, StoreRequest request) {
        Store store = requireStore(storeId);
        if (request.getName() != null && !request.getName().isBlank()) {
            store.setName(request.getName().trim());
        }
        if (request.getAddress() != null) {
            store.setAddress(request.getAddress());
        }
        if (request.getPhone() != null) {
            store.setPhone(request.getPhone());
        }
        if (request.getBusinessHours() != null) {
            store.setBusinessHours(request.getBusinessHours());
        }
        if (request.getStatus() != null) {
            store.setStatus(request.getStatus());
        }
        storeRepository.save(store);
        return toResponse(store);
    }

    @Override
    @Transactional
    public void deleteStore(Long storeId) {
        requireStore(storeId);
        storeRepository.deleteById(storeId);
    }

    @Override
    @Transactional
    public StoreResponse bindMerchant(Long storeId, Long merchantId) {
        if (merchantId == null) {
            throw new ServiceException(400, "商家 id 不能为空");
        }
        if (merchantRepository.findById(merchantId).isEmpty()) {
            throw new ServiceException(400, "商家不存在");
        }
        requireMerchantNotJoined(merchantId);

        Store store = requireStore(storeId);
        if (store.getMerchantId() != null) {
            throw new ServiceException(400, "该店铺已被其他商家入驻");
        }
        store.setMerchantId(merchantId);
        storeRepository.save(store);
        return toResponse(store);
    }

    @Override
    public List<StoreResponse> listByMerchant(Long merchantId) {
        if (merchantId == null) {
            throw new ServiceException(400, "商家 id 不能为空");
        }
        return storeRepository.findByMerchantId(merchantId).stream()
                .map(this::toResponse).toList();
    }

    private Store requireStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new ServiceException(400, "店铺不存在"));
    }

    /** 一商一店：商家已入驻其他店铺时拒绝 */
    private void requireMerchantNotJoined(Long merchantId) {
        if (!storeRepository.findByMerchantId(merchantId).isEmpty()) {
            throw new ServiceException(400, "该商家已入驻其他店铺");
        }
    }

    private StoreResponse toResponse(Store s) {
        return StoreResponse.from(s.getId(), s.getCode(), s.getName(), s.getAddress(),
                s.getPhone(), s.getBusinessHours(), s.getStatus(), s.getMerchantId());
    }
}
