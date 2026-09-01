package com.coffee.module.delivery.biz.application.service;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.security.PasswordEncoder;
import com.coffee.module.delivery.api.DeliveryContactParty;
import com.coffee.module.delivery.api.DeliveryService;
import com.coffee.module.delivery.api.dto.DeliveryAddressRequest;
import com.coffee.module.delivery.api.dto.DeliveryAddressResponse;
import com.coffee.module.delivery.api.dto.DeliveryOrderCreateRequest;
import com.coffee.module.delivery.api.dto.DeliveryOrderResponse;
import com.coffee.module.delivery.api.dto.DeliveryRiderLoginRequest;
import com.coffee.module.delivery.api.dto.DeliveryRiderPerformanceResponse;
import com.coffee.module.delivery.api.dto.DeliveryRiderProfileUpdateRequest;
import com.coffee.module.delivery.api.dto.DeliveryRiderRegisterRequest;
import com.coffee.module.delivery.api.dto.DeliveryRiderResponse;
import com.coffee.module.delivery.api.dto.VirtualCallResponse;
import com.coffee.module.delivery.api.event.DeliveryOrderStatusChangedEvent;
import com.coffee.module.delivery.biz.infra.persistence.DeliveryAddressMapper;
import com.coffee.module.delivery.biz.infra.persistence.DeliveryAddressPO;
import com.coffee.module.delivery.biz.infra.persistence.DeliveryOrderMapper;
import com.coffee.module.delivery.biz.infra.persistence.DeliveryOrderPO;
import com.coffee.module.delivery.biz.infra.persistence.DeliveryRiderMapper;
import com.coffee.module.delivery.biz.infra.persistence.DeliveryRiderPO;
import com.coffee.module.delivery.biz.service.VirtualCallRelayService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** 外卖模块应用服务。 */
@Service
public class DeliveryApplicationService implements DeliveryService {
    private static final int MAX_ADDRESS_COUNT = 20;
    private static final Pattern RIDER_USERNAME = Pattern.compile("[\\p{L}0-9_.-]{3,64}");
    private static final Pattern PHONE = Pattern.compile("[0-9+\\- ()]{6,30}");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,190}$");

    private final DeliveryAddressMapper addressMapper;
    private final DeliveryOrderMapper orderMapper;
    private final DeliveryRiderMapper riderMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final VirtualCallRelayService virtualCallRelayService;

    public DeliveryApplicationService(DeliveryAddressMapper addressMapper,
                                      DeliveryOrderMapper orderMapper,
                                      DeliveryRiderMapper riderMapper,
                                      ApplicationEventPublisher eventPublisher,
                                      VirtualCallRelayService virtualCallRelayService) {
        this.addressMapper = addressMapper;
        this.orderMapper = orderMapper;
        this.riderMapper = riderMapper;
        this.eventPublisher = eventPublisher;
        this.virtualCallRelayService = virtualCallRelayService;
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
        // 顾客下单时先建立配送单，但必须等商家完成制作后才公开给骑手。
        po.setStatus("WAITING_MERCHANT");
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
    @Transactional
    public void publishForRider(Long orderId) {
        if (orderId == null || orderId <= 0) throw new ServiceException(400, "关联订单无效");
        DeliveryOrderPO current = orderMapper.selectByOrderId(orderId);
        if (current == null) throw new ServiceException(409, "配送任务不存在，无法发布");
        if ("OPEN".equals(current.getStatus())) return;
        if (!"WAITING_MERCHANT".equals(current.getStatus())) {
            throw new ServiceException(409, "配送任务当前状态不能发布");
        }
        if (orderMapper.openIfMerchantReady(orderId) == 0) {
            DeliveryOrderPO latest = orderMapper.selectByOrderId(orderId);
            if (latest != null && "OPEN".equals(latest.getStatus())) return;
            throw new ServiceException(409, "订单尚未完成制作，暂不能发布配送任务");
        }
    }

    @Override
    @Transactional
    public void cancelForOrder(Long orderId) {
        if (orderId != null && orderId > 0) orderMapper.cancelPending(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryOrderResponse> listAvailableOrders() {
        return orderMapper.selectAvailable().stream().map(po -> toOrderResponse(po, true)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryOrderResponse> listRiderOrders(Long riderId) {
        requireActiveRider(riderId);
        return orderMapper.selectByRiderId(riderId).stream().map(po -> toOrderResponse(po, true)).toList();
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
        DeliveryOrderPO claimed = requireOrder(deliveryOrderId);
        eventPublisher.publishEvent(DeliveryOrderStatusChangedEvent.changed(
                claimed.getId(), claimed.getOrderId(), current.getStatus(), claimed.getStatus(), riderId));
        return toOrderResponse(claimed, true);
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
        DeliveryOrderPO latest = requireOrder(deliveryOrderId);
        eventPublisher.publishEvent(DeliveryOrderStatusChangedEvent.changed(
                latest.getId(), latest.getOrderId(), current.getStatus(), latest.getStatus(), riderId));
        return toOrderResponse(latest, true);
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

    @Override
    @Transactional
    public DeliveryRiderResponse updateRiderProfile(Long riderId, DeliveryRiderProfileUpdateRequest request) {
        if (request == null) throw new ServiceException(400, "资料请求不能为空");
        DeliveryRiderPO rider = requireActiveRider(riderId);
        if (request.getNickname() != null) {
            String nickname = normalizeProfile(request.getNickname(), 50, "昵称");
            if (nickname == null) throw new ServiceException(400, "昵称不能为空");
            rider.setNickname(nickname);
        }
        if (request.getPhone() != null) {
            String phone = normalizeProfile(request.getPhone(), 30, "联系电话");
            if (phone != null && !PHONE.matcher(phone).matches()) {
                throw new ServiceException(400, "请输入有效的联系电话");
            }
            rider.setPhone(phone);
        }
        if (request.getBirthday() != null && request.getBirthday().isAfter(LocalDate.now())) {
            throw new ServiceException(400, "生日不能晚于今天");
        }
        rider.setBirthday(request.getBirthday());
        if (request.getEmail() != null) {
            String email = normalizeProfile(request.getEmail(), 120, "邮箱");
            if (email != null && !EMAIL.matcher(email).matches()) {
                throw new ServiceException(400, "请输入有效的邮箱地址");
            }
            rider.setEmail(email);
        }
        if (request.getOtherInfo() != null) rider.setOtherInfo(normalizeProfile(request.getOtherInfo(), 500, "其他信息"));
        rider.setUpdatedAt(LocalDateTime.now());
        riderMapper.updateProfile(rider.getId(), rider.getNickname(), rider.getPhone(), rider.getBirthday(),
                rider.getEmail(), rider.getOtherInfo());
        return toRiderResponse(rider);
    }

    @Override
    @Transactional
    public DeliveryRiderResponse updateRiderAvatar(Long riderId, String avatarUrl) {
        DeliveryRiderPO rider = requireActiveRider(riderId);
        String normalized = normalizeProfile(avatarUrl, 500, "头像");
        if (normalized == null || !normalized.startsWith("/uploads/")) {
            throw new ServiceException(400, "头像地址无效");
        }
        rider.setAvatarUrl(normalized);
        rider.setUpdatedAt(LocalDateTime.now());
        riderMapper.updateAvatar(rider.getId(), rider.getAvatarUrl());
        return toRiderResponse(rider);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryRiderPerformanceResponse getRiderPerformance(Long riderId) {
        return getRiderPerformance(riderId, "7d");
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryRiderPerformanceResponse getRiderPerformance(Long riderId, String range) {
        requireActiveRider(riderId);
        PerformanceWindow window = resolvePerformanceWindow(range);
        Map<String, Object> summary = orderMapper.selectRiderPerformance(
                riderId, window.startAt(), window.endAt());
        DeliveryRiderPerformanceResponse response = new DeliveryRiderPerformanceResponse();
        response.setRange(window.key());
        response.setRangeLabel(window.label());
        response.setBucket(window.bucket());
        response.setRangeAssigned(metric(summary, "rangeAssigned"));
        int rangeDelivered = metric(summary, "rangeDelivered");
        BigDecimal rangeAmount = money(summary, "rangeAmount");
        response.setRangeDelivered(rangeDelivered);
        response.setRangeAmount(rangeAmount);
        response.setAverageOrderAmount(rangeDelivered == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : rangeAmount.divide(BigDecimal.valueOf(rangeDelivered), 2, RoundingMode.HALF_UP));
        response.setTodayAssigned(metric(summary, "todayAssigned"));
        response.setTodayDelivered(metric(summary, "todayDelivered"));
        response.setActiveOrders(metric(summary, "activeOrders"));
        response.setWeekDelivered(metric(summary, "weekDelivered"));
        response.setTotalDelivered(metric(summary, "totalDelivered"));
        response.setTotalDeliveredAmount(money(summary, "totalDeliveredAmount"));
        response.setDeliveryFeeConfigured(false);
        response.setDeliveryFeeLabel("配送费规则待接入");

        List<Map<String, Object>> rows = "WEEK".equals(window.bucket())
                ? orderMapper.selectWeeklyDelivered(riderId, window.startAt(), window.endAt())
                : orderMapper.selectDailyDelivered(riderId, window.startAt(), window.endAt());
        Map<String, DeliveryRiderPerformanceResponse.DailyPoint> byDay = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object day = value(row, "day");
            if (day != null) {
                String dayKey = String.valueOf(day);
                byDay.put(dayKey, new DeliveryRiderPerformanceResponse.DailyPoint(
                        dayKey, metric(row, "delivered"), money(row, "amount")));
            }
        }
        List<DeliveryRiderPerformanceResponse.DailyPoint> daily = new ArrayList<>();
        for (int index = 0; index < window.bucketCount(); index++) {
            LocalDate day = "WEEK".equals(window.bucket())
                    ? window.startDate().plusWeeks(index)
                    : window.startDate().plusDays(index);
            String dayKey = day.format(DateTimeFormatter.BASIC_ISO_DATE);
            daily.add(byDay.getOrDefault(dayKey,
                    new DeliveryRiderPerformanceResponse.DailyPoint(dayKey, 0, BigDecimal.ZERO.setScale(2))));
        }
        response.setDaily(daily);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public VirtualCallResponse requestCustomerCall(Long orderId, Long userId) {
        requireUserId(userId);
        DeliveryOrderPO current = requireOrderByOrderId(orderId);
        if (!userId.equals(current.getUserId())) throw new ServiceException(403, "无权联系该订单的配送员");
        ensureContactable(current);
        return virtualCallRelayService.start(current, DeliveryContactParty.CUSTOMER);
    }

    @Override
    @Transactional(readOnly = true)
    public VirtualCallResponse requestRiderCall(Long deliveryOrderId, Long riderId) {
        requireActiveRider(riderId);
        DeliveryOrderPO current = requireOrder(deliveryOrderId);
        if (!riderId.equals(current.getRiderId())) throw new ServiceException(403, "这笔订单不属于当前配送员");
        ensureContactable(current);
        return virtualCallRelayService.start(current, DeliveryContactParty.RIDER);
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

    private DeliveryOrderPO requireOrderByOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) throw new ServiceException(400, "订单无效");
        DeliveryOrderPO po = orderMapper.selectByOrderId(orderId);
        if (po == null) throw new ServiceException(404, "配送单不存在");
        return po;
    }

    private void ensureContactable(DeliveryOrderPO order) {
        if (!List.of("CLAIMED", "PICKED_UP", "DELIVERING").contains(order.getStatus())) {
            throw new ServiceException(409, "骑手尚未接单，暂不能发起联系");
        }
    }

    private int metric(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private BigDecimal money(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        try {
            return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private Object value(Map<String, Object> row, String key) {
        if (row == null) return null;
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase(Locale.ROOT));
        if (value == null) value = row.get(toSnakeCase(key));
        if (value == null) value = row.get(toSnakeCase(key).toUpperCase(Locale.ROOT));
        return value;
    }

    private String toSnakeCase(String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private PerformanceWindow resolvePerformanceWindow(String rawRange) {
        String range = rawRange == null ? "" : rawRange.trim().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        return switch (range) {
            case "14d", "14" -> dayWindow("14d", "近 14 天", 14, today);
            case "28d", "30d", "month", "1m" -> dayWindow("28d", "近 1 个月", 28, today);
            case "12w", "90d", "quarter", "3m" -> {
                LocalDate thisMonday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
                LocalDate start = thisMonday.minusWeeks(11);
                yield new PerformanceWindow("12w", "近 1 个季度", "WEEK", start, thisMonday.plusWeeks(1), 12);
            }
            case "7d", "7", "" -> dayWindow("7d", "近 7 天", 7, today);
            default -> dayWindow("7d", "近 7 天", 7, today);
        };
    }

    private PerformanceWindow dayWindow(String key, String label, int days, LocalDate today) {
        return new PerformanceWindow(key, label, "DAY", today.minusDays(days - 1L), today.plusDays(1), days);
    }

    private record PerformanceWindow(String key, String label, String bucket,
                                     LocalDate startDate, LocalDate endDateExclusive, int bucketCount) {
        private LocalDateTime startAt() {
            return startDate.atStartOfDay();
        }

        private LocalDateTime endAt() {
            return endDateExclusive.atStartOfDay();
        }
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
        return toOrderResponse(po, false);
    }

    private DeliveryOrderResponse toOrderResponse(DeliveryOrderPO po, boolean riderView) {
        DeliveryOrderResponse response = new DeliveryOrderResponse();
        response.setId(po.getId());
        response.setDeliveryOrderId(po.getId());
        response.setOrderId(po.getOrderId());
        response.setOrderNo(po.getOrderNo());
        response.setUserId(riderView ? null : po.getUserId());
        response.setStoreId(po.getStoreId());
        response.setStoreName(po.getStoreName());
        response.setAmount(po.getAmount());
        response.setItemSummary(po.getItemSummary());
        response.setNote(po.getNote());
        response.setAddressLabel(po.getAddressLabel());
        response.setReceiverName(po.getReceiverName());
        response.setReceiverPhone(riderView ? null : po.getReceiverPhone());
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
        DeliveryRiderResponse response = DeliveryRiderResponse.ok(
                po.getId(), po.getUsername(), po.getNickname(), po.getPhone(), po.getStatus());
        response.setAvatarUrl(po.getAvatarUrl());
        response.setBirthday(po.getBirthday());
        response.setEmail(po.getEmail());
        response.setOtherInfo(po.getOtherInfo());
        return response;
    }

    private String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "WAITING_MERCHANT" -> "等待商家完成制作";
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

    private String normalizeProfile(String value, int maxLength, String field) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > maxLength) {
            throw new ServiceException(400, field + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
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
