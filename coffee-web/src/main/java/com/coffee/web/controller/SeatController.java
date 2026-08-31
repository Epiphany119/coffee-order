package com.coffee.web.controller;

import com.coffee.module.seat.api.SeatService;
import com.coffee.module.seat.api.dto.AssignSeatRequest;
import com.coffee.module.seat.api.dto.OccupySeatRequest;
import com.coffee.module.seat.api.dto.SeatResponse;
import com.coffee.module.store.api.StoreService;
import com.coffee.web.security.AccessGuard;
import com.coffee.web.security.RequestIdentity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 座位控制器（分配 / 扫码落座 / 离座释放）
 */
@RestController
@RequestMapping("/api/seat")
public class SeatController {

    private final SeatService seatService;
    private final StoreService storeService;

    public SeatController(SeatService seatService, StoreService storeService) {
        this.seatService = seatService;
        this.storeService = storeService;
    }

    /** 按人数分配空闲座位，返回座位信息与落座二维码 */
    @PostMapping("/assign")
    public SeatResponse assign(@RequestBody AssignSeatRequest request) {
        bindCustomerIdentity(request);
        return seatService.assignSeat(request);
    }

    /** 解析二维码内容（座位编号），返回座位状态 */
    @GetMapping("/resolve")
    public SeatResponse resolve(@RequestParam("code") String code) {
        return seatService.resolveSeat(code);
    }

    /** 确认落座（扫码后调用） */
    @PostMapping("/{id}/occupy")
    public SeatResponse occupy(@PathVariable("id") Long id, @RequestBody OccupySeatRequest request) {
        bindCustomerIdentity(request);
        return seatService.occupySeat(id, request);
    }

    /** 离座释放 */
    @PostMapping("/{id}/leave")
    public SeatResponse leave(@PathVariable("id") Long id) {
        RequestIdentity identity = requireCustomerIdentity();
        return seatService.leaveSeat(id, identity.id(), identity.guestId());
    }

    /** 店铺座位状态列表（storeId 为空查全部） */
    @GetMapping("/list")
    public List<SeatResponse> list(@RequestParam(value = "storeId", required = false) Long storeId) {
        if (storeId == null) throw new com.coffee.common.core.exception.ServiceException(400, "请指定店铺");
        requireStoreOwner(storeId);
        return seatService.listSeats(storeId);
    }

    /** 按身份查当前店已落座座位（前端幽灵占座恢复：本地无座位时找回自己占的座） */
    @GetMapping("/occupied")
    public List<SeatResponse> occupied(@RequestParam("storeId") Long storeId,
                                       @RequestParam(value = "userId", required = false) Long userId,
                                       @RequestParam(value = "guestId", required = false) String guestId) {
        RequestIdentity identity = requireCustomerIdentity();
        return seatService.listOccupiedSeats(storeId, identity.id(), identity.guestId());
    }

    private void bindCustomerIdentity(AssignSeatRequest request) {
        if (request == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        RequestIdentity identity = requireCustomerIdentity();
        request.setUserId(identity.id());
        request.setGuestId(identity.guestId());
    }

    private void bindCustomerIdentity(OccupySeatRequest request) {
        if (request == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        RequestIdentity identity = requireCustomerIdentity();
        request.setUserId(identity.id());
        request.setGuestId(identity.guestId());
    }

    private RequestIdentity requireCustomerIdentity() {
        RequestIdentity identity = AccessGuard.currentIdentity();
        if (identity.kind() == RequestIdentity.Kind.MERCHANT) {
            throw new com.coffee.common.core.exception.ServiceException(403, "商家身份不能执行顾客座位操作");
        }
        if (identity.kind() == RequestIdentity.Kind.USER && identity.id() == null) {
            throw new com.coffee.common.core.exception.ServiceException(401, "用户身份无效");
        }
        if (identity.kind() == RequestIdentity.Kind.GUEST
                && (identity.guestId() == null || identity.guestId().isBlank())) {
            throw new com.coffee.common.core.exception.ServiceException(401, "游客身份无效");
        }
        return identity;
    }

    private void requireStoreOwner(Long storeId) {
        Long merchantId = AccessGuard.currentMerchantId();
        boolean ownsStore = storeService.listByMerchant(merchantId).stream()
                .anyMatch(store -> storeId.equals(store.getStoreId()));
        if (!ownsStore) throw new com.coffee.common.core.exception.ServiceException(403, "无权查看其他店铺座位");
    }
}
