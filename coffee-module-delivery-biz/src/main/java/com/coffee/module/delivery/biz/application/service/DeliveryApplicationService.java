package com.coffee.module.delivery.biz.application.service;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.security.PasswordEncoder;
import com.coffee.module.delivery.api.DeliveryService;
import com.coffee.module.delivery.api.dto.DeliveryAddressRequest;
import com.coffee.module.delivery.api.dto.DeliveryAddressResponse;
import com.coffee.module.delivery.api.dto.DeliveryOrderCreateRequest;
import com.coffee.module.delivery.api.dto.DeliveryOrderResponse;
import com.coffee.module.delivery.api.dto.DeliveryRiderLoginRequest;
import com.coffee.module.delivery.api.dto.DeliveryRiderRegisterRequest;
import com.coffee.module.delivery.api.dto.DeliveryRiderResponse;
import com.coffee.module.delivery.biz.infra.persistence.DeliveryAddressMapper;
import com.coffee.module.delivery.biz.infra.persistence.DeliveryAddressPO;
import com.coffee.module.delivery.biz.infra.persistence.DeliveryOrderMapper;
import com.coffee.module.delivery.biz.infra.persistence.DeliveryOrderPO;
import com.coffee.module.delivery.biz.infra.persistence.DeliveryRiderMapper;
import com.coffee.module.delivery.biz.infra.persistence.DeliveryRiderPO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 外卖模块应用服务。 */
@Service
public class DeliveryApplicationService implements DeliveryService {
    private static final int MAX_ADDRESS_COUNT = 20;
    private static final Pattern RIDER_USERNAME = Pattern.compile("[\\p{L}0-9_.-]{3,64}");
    private static final Pattern PHONE = Pattern.compile("[0-9+\\- ()]{6,30}");

    private final DeliveryAddressMapper addressMapper;
    private final DeliveryOrderMapper orderMapper;
    private final DeliveryRiderMapper riderMapper;

    public DeliveryApplicationService(DeliveryAddressMapper addressMapper,
                                      DeliveryOrderMapper orderMapper,
                                      DeliveryRiderMapper riderMapper) {
        this.addressMapper = addressMapper;
        this.orderMapper = orderMapper;
        this.riderMapper = riderMapper;
    }

    // ======================== 顾客地址 ========================

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryAddressResponse> listAddresses(Long userId) {
        requireUserId(userId);
        return addressMapper.selectByUserId(userId).stream().map(this::toAddressResponse).toList();
    }

    @Override
    @Transactional
    public DeliveryAddressResponse createAddress(Long userId, DeliveryAddressRequest request) {
        requireUserId(userId);
        validateAddress(request);
        long existing = addressMapper.countByUserId(userId);
        if (existing >= MAX_ADDRESS_COUNT) {
            throw new ServiceException(400, "最多保存" + MAX_ADDRESS_COUNT + "个收货地址");
        }

        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault()) || existing == 0;
        if (makeDefault) addressMapper.clearDefault(userId);

        DeliveryAddressPO po = new DeliveryAddressPO();
        po.setUserId(userId);
        applyAddress(po, request);
        po.setIsDefault(makeDefault);
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(po.getCreatedAt());
        addressMapper.insert(po);
        return toAddressResponse(po);
    }

    @Override
    @Transactional
    public DeliveryAddressResponse updateAddress(Long userId, Long addressId, DeliveryAddressRequest request) {
        requireUserId(userId);
        validateAddress(request);
        DeliveryAddressPO po = requireOwnedAddress(userId, addressId);
        boolean wasDefault = Boolean.TRUE.equals(po.getIsDefault());
        boolean makeDefault = request.getIsDefault() == null
                ? wasDefault
                : Boolean.TRUE.equals(request.getIsDefault());
        if (makeDefault) {
            addressMapper.clearDefault(userId);
        } else if (wasDefault) {
            // 不允许用户把唯一默认地址取消到“没有默认地址”；若有其他地址则把默认项顺延给它。
            DeliveryAddressPO other = addressMapper.selectFirstOther(userId, addressId);
            if (other == null) makeDefault = true;
            addressMapper.clearDefault(userId);
            if (other != null) addressMapper.markDefault(userId, other.getId());
        }
        applyAddress(po, request);
        po.setIsDefault(makeDefault);
        po.setUpdatedAt(LocalDateTime.now());
        addressMapper.updateById(po);
        return toAddressResponse(po);
    }

    @Override
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        requireUserId(userId);
        DeliveryAddressPO po = requireOwnedAddress(userId, addressId);
        boolean wasDefault = Boolean.TRUE.equals(po.getIsDefault());
        if (addressMapper.deleteOwned(userId, addressId) == 0) {
            throw new ServiceException(404, "地址不存在");
        }
        if (wasDefault) {
            DeliveryAddressPO first = addressMapper.selectFirstByUserId(userId);
            if (first != null) addressMapper.markDefault(userId, first.getId());
        }
    }

    // ======================== 配送单 ========================

    @Override
    @Transactional
    public DeliveryOrderResponse createDeliveryOrder(DeliveryOrderCreateRequest request) {
        if (request == null) throw new ServiceException(400, "配送单请求不能为空");
        requireUserId(request.getUserId());
        if (request.getOrderId() == null || request.getOrderId() <= 0) {
            throw new ServiceException(400, "关联订单无效");
        }
        if (request.getStoreId() == null || request.getStoreId() <= 0) {
            throw new ServiceException(400, "配送门店无效");
        }
        if (request.getAddressId() == null || request.getAddressId() <= 0) {
            throw new ServiceException(400, "请选择收货地址");
        }
        if (request.getOrderNo() == null || request.getOrderNo().isBlank()) {
            throw new ServiceException(400, "订单号不能为空");
        }
        if (request.getItemSummary() == null || request.getItemSummary().isBlank()) {
            throw new ServiceException(400, "配送商品不能为空");
        }

        DeliveryOrderPO existing = orderMapper.selectByOrderId(request.getOrderId());
        if (existing != null) {
            if (!request.getUserId().equals(existing.getUserId())) {
                throw new ServiceException(403, "无权访问该配送单");
            }
            return toOrderResponse(existing);
        }

        DeliveryAddressPO address = requireOwnedAddress(request.getUserId(), request.getAddressId());
        DeliveryOrderPO po = new DeliveryOrderPO();
        po.setOrderId(request.getOrderId());
        po.setOrderNo(trim(request.getOrderNo(), 64));
        po.setUserId(request.getUserId());
        po.setStoreId(request.getStoreId());
        po.setStoreName(trim(request.getStoreName(), 120));
        po.setAmount(request.getAmount() == null ? 0.0 : Math.max(0.0, request.getAmount()));
        po.setItemSummary(trim(request.getItemSummary(), 500));
        po.setNote(trim(request.getNote(), 500));
        po.setAddressLabel(address.getLabel());
        po.setReceiverName(address.getReceiverName());
        po.setReceiverPhone(address.getReceiverPhone());
        po.setDetailAddress(address.getDetailAddress());
        po.setStatus("OPEN");
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(po.getCreatedAt());
        try {
            orderMapper.insert(po);
        } catch (DuplicateKeyException e) {
            // 订单幂等重试可能同时进入这里；读取已经落库的配送单即可。
            DeliveryOrderPO concurrent = orderMapper.selectByOrderId(request.getOrderId());
            if (concurrent == null) throw e;
            return toOrderResponse(concurrent);
        }
        return toOrderResponse(po);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryOrderResponse> listAvailableOrders() {
        return orderMapper.selectAvailable().stream().map(this::toOrderResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryOrderResponse> listRiderOrders(Long riderId) {
        requireActiveRider(riderId);
        return orderMapper.selectByRiderId(riderId).stream().map(this::toOrderResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryOrderResponse> listCustomerOrders(Long userId) {
        requireUserId(userId);
        return orderMapper.selectByUserId(userId).stream().map(this::toOrderResponse).toList();
    }

    @Override
    @Transactional
    public DeliveryOrderResponse claimOrder(Long deliveryOrderId, Long riderId) {
        DeliveryRiderPO rider = requireActiveRider(riderId);
        DeliveryOrderPO current = requireOrder(deliveryOrderId);
        if (!"OPEN".equals(current.getStatus())) {
            throw new ServiceException(409, "这笔订单已被其他配送员抢走，请刷新列表");
        }
        if (orderMapper.claimIfOpen(deliveryOrderId, riderId, displayRiderName(rider)) == 0) {
            throw new ServiceException(409, "这笔订单刚刚被其他配送员抢走，请刷新列表");
        }
        return toOrderResponse(requireOrder(deliveryOrderId));
    }

    @Override
    @Transactional
    public DeliveryOrderResponse action(Long deliveryOrderId, Long riderId, String action) {
        requireActiveRider(riderId);
        DeliveryOrderPO current = requireOrder(deliveryOrderId);
        if (!riderId.equals(current.getRiderId())) {
            throw new ServiceException(403, "这笔订单不属于当前配送员");
        }
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        int affected;
        switch (normalized) {
            case "pickup", "picked_up" -> affected = orderMapper.markPickedUp(deliveryOrderId, riderId);
            case "deliver", "delivering" -> affected = orderMapper.markDelivering(deliveryOrderId, riderId);
            case "complete", "completed", "delivered" -> affected = orderMapper.markDelivered(deliveryOrderId, riderId);
            case "release", "unclaim" -> affected = orderMapper.releaseClaim(deliveryOrderId, riderId);
            default -> throw new ServiceException(400, "不支持的配送操作");
        }
        if (affected == 0) {
            throw new ServiceException(409, "订单状态已变化，请刷新后重试");
        }
        return toOrderResponse(requireOrder(deliveryOrderId));
    }

    // ======================== 配送员账号 ========================

    @Override
    @Transactional
    public DeliveryRiderResponse register(DeliveryRiderRegisterRequest request) {
        if (request == null) return DeliveryRiderResponse.fail("请求不能为空");
        String username = normalizeUsername(request.getUsername());
        String password = request.getPassword();
        if (username == null || !RIDER_USERNAME.matcher(username).matches()) {
            return DeliveryRiderResponse.fail("配送员账号需为 3-64 位字母、数字或 ._- 字符");
        }
        try {
            validatePassword(password);
        } catch (IllegalArgumentException e) {
            return DeliveryRiderResponse.fail(e.getMessage());
        }
        if (riderMapper.selectByUsername(username) != null) {
            return DeliveryRiderResponse.fail("该配送员账号已存在");
        }

        DeliveryRiderPO po = new DeliveryRiderPO();
        po.setUsername(username);
        po.setPasswordHash(PasswordEncoder.encode(password));
        po.setNickname(normalizeNickname(request.getNickname(), username));
        po.setPhone(normalizePhone(request.getPhone()));
        po.setStatus("ACTIVE");
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(po.getCreatedAt());
        try {
            riderMapper.insert(po);
        } catch (DuplicateKeyException e) {
            return DeliveryRiderResponse.fail("该配送员账号已存在");
        }
        return toRiderResponse(po);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryRiderResponse login(DeliveryRiderLoginRequest request) {
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            return DeliveryRiderResponse.fail("账号或密码错误");
        }
        DeliveryRiderPO po = riderMapper.selectByUsername(request.getUsername().trim());
        if (po == null || !PasswordEncoder.matches(request.getPassword(), po.getPasswordHash())) {
            return DeliveryRiderResponse.fail("账号或密码错误");
        }
        if (!"ACTIVE".equalsIgnoreCase(po.getStatus())) {
            return DeliveryRiderResponse.fail("配送员账号已停用");
        }
        return toRiderResponse(po);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryRiderResponse getRider(Long riderId) {
        return toRiderResponse(requireActiveRider(riderId));
    }

    private DeliveryAddressPO requireOwnedAddress(Long userId, Long addressId) {
        if (addressId == null || addressId <= 0) throw new ServiceException(400, "地址无效");
        DeliveryAddressPO po = addressMapper.selectOwned(userId, addressId);
        if (po == null) throw new ServiceException(404, "收货地址不存在");
        return po;
    }

    private DeliveryOrderPO requireOrder(Long deliveryOrderId) {
        if (deliveryOrderId == null || deliveryOrderId <= 0) throw new ServiceException(400, "配送单无效");
        DeliveryOrderPO po = orderMapper.selectById(deliveryOrderId);
        if (po == null) throw new ServiceException(404, "配送单不存在");
        return po;
    }

    private DeliveryRiderPO requireActiveRider(Long riderId) {
        if (riderId == null || riderId <= 0) throw new ServiceException(401, "请先登录配送员账号");
        DeliveryRiderPO po = riderMapper.selectById(riderId);
        if (po == null) throw new ServiceException(401, "配送员不存在");
        if (!"ACTIVE".equalsIgnoreCase(po.getStatus())) {
            throw new ServiceException(403, "配送员账号已停用");
        }
        return po;
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) throw new ServiceException(400, "用户身份无效");
    }

    private void validateAddress(DeliveryAddressRequest request) {
        if (request == null) throw new ServiceException(400, "地址请求不能为空");
        if (blankOrTooLong(request.getLabel(), 30)) throw new ServiceException(400, "地址标签需为 1-30 个字符");
        if (blankOrTooLong(request.getReceiverName(), 50)) throw new ServiceException(400, "收货人需为 1-50 个字符");
        String phone = trim(request.getReceiverPhone(), 30);
        if (phone == null || !PHONE.matcher(phone).matches()) throw new ServiceException(400, "请输入有效的联系电话");
        if (blankOrTooLong(request.getDetailAddress(), 200)) throw new ServiceException(400, "详细地址需为 1-200 个字符");
    }

    private void applyAddress(DeliveryAddressPO po, DeliveryAddressRequest request) {
        po.setLabel(trim(request.getLabel(), 30));
        po.setReceiverName(trim(request.getReceiverName(), 50));
        po.setReceiverPhone(trim(request.getReceiverPhone(), 30));
        po.setDetailAddress(trim(request.getDetailAddress(), 200));
    }

    private DeliveryAddressResponse toAddressResponse(DeliveryAddressPO po) {
        DeliveryAddressResponse response = new DeliveryAddressResponse();
        response.setId(po.getId());
        response.setLabel(po.getLabel());
        response.setReceiverName(po.getReceiverName());
        response.setReceiverPhone(po.getReceiverPhone());
        response.setDetailAddress(po.getDetailAddress());
        response.setIsDefault(Boolean.TRUE.equals(po.getIsDefault()));
        response.setCreatedAt(po.getCreatedAt());
        response.setUpdatedAt(po.getUpdatedAt());
        return response;
    }

    private DeliveryOrderResponse toOrderResponse(DeliveryOrderPO po) {
        DeliveryOrderResponse response = new DeliveryOrderResponse();
        response.setId(po.getId());
        response.setDeliveryOrderId(po.getId());
        response.setOrderId(po.getOrderId());
        response.setOrderNo(po.getOrderNo());
        response.setUserId(po.getUserId());
        response.setStoreId(po.getStoreId());
        response.setStoreName(po.getStoreName());
        response.setAmount(po.getAmount());
        response.setItemSummary(po.getItemSummary());
        response.setNote(po.getNote());
        response.setAddressLabel(po.getAddressLabel());
        response.setReceiverName(po.getReceiverName());
        response.setReceiverPhone(po.getReceiverPhone());
        response.setDetailAddress(po.getDetailAddress());
        response.setStatus(po.getStatus());
        response.setStatusLabel(statusLabel(po.getStatus()));
        response.setRiderId(po.getRiderId());
        response.setRiderName(po.getRiderName());
        response.setCreatedAt(po.getCreatedAt());
        response.setClaimedAt(po.getClaimedAt());
        response.setPickedUpAt(po.getPickedUpAt());
        response.setDeliveredAt(po.getDeliveredAt());
        response.setUpdatedAt(po.getUpdatedAt());
        return response;
    }

    private DeliveryRiderResponse toRiderResponse(DeliveryRiderPO po) {
        return DeliveryRiderResponse.ok(po.getId(), po.getUsername(), po.getNickname(), po.getPhone(), po.getStatus());
    }

    private String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "OPEN" -> "待抢单";
            case "CLAIMED" -> "已抢单";
            case "PICKED_UP" -> "已取餐";
            case "DELIVERING" -> "配送中";
            case "DELIVERED" -> "已送达";
            case "CANCELED" -> "已取消";
            default -> status == null ? "未知状态" : status;
        };
    }

    private String displayRiderName(DeliveryRiderPO rider) {
        return rider.getNickname() == null || rider.getNickname().isBlank() ? rider.getUsername() : rider.getNickname();
    }

    private String normalizeUsername(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeNickname(String value, String fallback) {
        String nickname = trim(value, 50);
        return nickname == null ? fallback : nickname;
    }

    private String normalizePhone(String value) {
        return trim(value, 30);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) throw new IllegalArgumentException("密码至少需要 6 个字符");
        if (!password.matches(".*[a-zA-Z].*")) throw new IllegalArgumentException("密码必须包含至少一个字母");
        if (!password.matches(".*[0-9].*")) throw new IllegalArgumentException("密码必须包含至少一个数字");
    }

    private boolean blankOrTooLong(String value, int max) {
        return value == null || value.trim().isEmpty() || value.trim().length() > max;
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
