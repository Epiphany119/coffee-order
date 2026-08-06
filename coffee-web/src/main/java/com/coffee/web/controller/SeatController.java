package com.coffee.web.controller;

import com.coffee.module.seat.api.SeatService;
import com.coffee.module.seat.api.dto.AssignSeatRequest;
import com.coffee.module.seat.api.dto.OccupySeatRequest;
import com.coffee.module.seat.api.dto.SeatResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 座位控制器（分配 / 扫码落座 / 离座释放）
 */
@RestController
@RequestMapping("/api/seat")
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    /** 按人数分配空闲座位，返回座位信息与落座二维码 */
    @PostMapping("/assign")
    public SeatResponse assign(@RequestBody AssignSeatRequest request) {
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
        return seatService.occupySeat(id, request);
    }

    /** 离座释放 */
    @PostMapping("/{id}/leave")
    public SeatResponse leave(@PathVariable("id") Long id) {
        return seatService.leaveSeat(id);
    }

    /** 店铺座位状态列表（storeId 为空查全部） */
    @GetMapping("/list")
    public List<SeatResponse> list(@RequestParam(value = "storeId", required = false) Long storeId) {
        return seatService.listSeats(storeId);
    }

    /** 按身份查当前店已落座座位（前端幽灵占座恢复：本地无座位时找回自己占的座） */
    @GetMapping("/occupied")
    public List<SeatResponse> occupied(@RequestParam("storeId") Long storeId,
                                       @RequestParam(value = "userId", required = false) Long userId,
                                       @RequestParam(value = "guestId", required = false) String guestId) {
        return seatService.listOccupiedSeats(storeId, userId, guestId);
    }
}
