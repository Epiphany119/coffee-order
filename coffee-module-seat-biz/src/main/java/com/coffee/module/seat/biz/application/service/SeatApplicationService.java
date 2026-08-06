package com.coffee.module.seat.biz.application.service;

import com.coffee.module.seat.api.SeatService;
import com.coffee.module.seat.api.dto.AssignSeatRequest;
import com.coffee.module.seat.api.dto.OccupySeatRequest;
import com.coffee.module.seat.api.dto.SeatResponse;
import com.coffee.module.seat.api.dto.SeatStatus;
import com.coffee.module.seat.biz.domain.Seat;
import com.coffee.module.seat.biz.domain.repository.SeatRepository;
import com.coffee.module.seat.biz.infra.qrcode.QrCodeGenerator;
import com.coffee.module.seat.config.SeatProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 座位应用服务：人数分配 / 扫码落座 / 离座释放 / 超时释放
 */
@Service
public class SeatApplicationService implements SeatService {

    private static final Logger log = LoggerFactory.getLogger(SeatApplicationService.class);

    /** 最多支持人数（多人桌容量） */
    private static final int MAX_PEOPLE = 8;
    private static final int TWO_SEAT_CAPACITY = 2;
    private static final int FOUR_SEAT_CAPACITY = 4;

    private final SeatRepository seatRepository;
    private final QrCodeGenerator qrCodeGenerator;
    private final SeatProperties properties;

    public SeatApplicationService(SeatRepository seatRepository,
                                  QrCodeGenerator qrCodeGenerator,
                                  SeatProperties properties) {
        this.seatRepository = seatRepository;
        this.qrCodeGenerator = qrCodeGenerator;
        this.properties = properties;
    }

    @Override
    @Transactional
    public SeatResponse assignSeat(AssignSeatRequest request) {
        if (request.getStoreId() == null) {
            throw new IllegalArgumentException("请先选择店铺");
        }
        int peopleCount = request.getPeopleCount();
        if (peopleCount < 1 || peopleCount > MAX_PEOPLE) {
            throw new IllegalArgumentException("就餐人数需在 1-" + MAX_PEOPLE + " 人之间");
        }

        // 一人一桌：该身份在本店已有未释放座位（已分配/已落座）时直接返回已有座位，
        // 防止用户重复取号（如刷新/重进/换账号残留）导致同一身份占多桌
        List<Seat> existing = seatRepository.findOccupiedByStoreAndIdentity(
                request.getStoreId(), request.getUserId(), request.getGuestId());
        if (!existing.isEmpty()) {
            log.info("[seat] 复用已有座位 store={} code={} userId={} guestId={}（一人一桌）",
                    request.getStoreId(), existing.get(0).code(), request.getUserId(), request.getGuestId());
            return toResponse(existing.get(0), true);
        }

        int requiredCapacity = requiredCapacity(peopleCount);
        List<Seat> candidates = seatRepository.findFreeSeats(request.getStoreId(), requiredCapacity, 50);

        if (candidates.isEmpty()) {
            throw new IllegalStateException("当前没有可用的座位，请稍后再试");
        }

        Seat assigned = null;
        for (Seat seat : candidates) {
            // 乐观锁：并发下仅一张请求能分配成功
            if (seatRepository.tryAssign(seat.getId(), request.getUserId(), request.getGuestId())) {
                assigned = seat;
                break;
            }
        }

        if (assigned == null) {
            throw new IllegalStateException("座位分配失败，请稍后再试");
        }

        assigned.assignTo(request.getUserId(), request.getGuestId());

        log.info("[seat] 分配座位 store={} code={} people={} userId={} guestId={}",
                request.getStoreId(), assigned.code(), peopleCount, request.getUserId(), request.getGuestId());

        return toResponse(assigned, true);
    }

    @Override
    public SeatResponse resolveSeat(String code) {
        Seat seat = findByCode(code);
        return toResponse(seat, false);
    }

    @Override
    @Transactional
    public SeatResponse occupySeat(Long seatId, OccupySeatRequest request) {
        Seat seat = seatRepository.findById(seatId);
        if (seat == null) {
            throw new IllegalArgumentException("座位不存在");
        }
        // 一人一桌：该身份在本店已有其他未释放座位时拒绝落座，防止同一身份同时占多桌
        List<Seat> existing = seatRepository.findOccupiedByStoreAndIdentity(
                seat.getStoreId(), request.getUserId(), request.getGuestId());
        for (Seat s : existing) {
            if (!s.getId().equals(seatId)) {
                throw new IllegalStateException("你已在「" + s.code() + "」取号/落座，请先离座释放再落座新座位");
            }
        }
        // 座位编号即凭证：任何状态（空闲/已分配/已被他人占用）扫码均可直接落座。
        // 落座即记录占用者，前端据此校验座位归属，避免跨账号继承他人座位状态
        if (!seatRepository.occupy(seatId, request.getUserId(), request.getGuestId())) {
            throw new IllegalStateException("座位状态已变化，请重新扫码");
        }
        seat.occupy(request.getUserId(), request.getGuestId());

        log.info("[seat] 落座 code={}", seat.code());
        return toResponse(seat, false);
    }

    @Override
    @Transactional
    public SeatResponse leaveSeat(Long seatId) {
        Seat seat = seatRepository.findById(seatId);
        if (seat == null) {
            throw new IllegalArgumentException("座位不存在");
        }
        if (seat.getStatus() != SeatStatus.OCCUPIED) {
            throw new IllegalStateException("该座位当前未落座");
        }
        if (!seatRepository.leave(seatId)) {
            throw new IllegalStateException("座位状态已变化，请刷新后重试");
        }
        seat.leave();

        log.info("[seat] 离座释放 code={}", seat.code());
        return toResponse(seat, false);
    }

    @Override
    public List<SeatResponse> listSeats(Long storeId) {
        return seatRepository.findAll(storeId).stream()
                .map(s -> toResponse(s, false))
                .collect(Collectors.toList());
    }

    @Override
    public List<SeatResponse> listOccupiedSeats(Long storeId, Long userId, String guestId) {
        if (storeId == null) {
            throw new IllegalArgumentException("请先选择店铺");
        }
        // 无身份标识时无从恢复，直接返回空
        if (userId == null && (guestId == null || guestId.isBlank())) {
            return List.of();
        }
        return seatRepository.findOccupiedByStoreAndIdentity(storeId, userId, guestId).stream()
                .map(s -> toResponse(s, false))
                .collect(Collectors.toList());
    }

    /**
     * 定时任务：每分钟释放超时未落座的已分配座位
     */
    @Scheduled(cron = "0 * * * * ?")
    @Transactional
    public void releaseExpiredAssignments() {
        LocalDateTime deadline = LocalDateTime.now()
                .minusMinutes(properties.getAssignTimeoutMinutes());
        int released = seatRepository.releaseExpired(deadline);
        if (released > 0) {
            log.info("[seat] 超时释放 {} 个座位（超时 {} 分钟）",
                    released, properties.getAssignTimeoutMinutes());
        }
    }

    /** 按人数取所需桌型容量 */
    private int requiredCapacity(int peopleCount) {
        if (peopleCount <= TWO_SEAT_CAPACITY) return TWO_SEAT_CAPACITY;
        if (peopleCount <= FOUR_SEAT_CAPACITY) return FOUR_SEAT_CAPACITY;
        return MAX_PEOPLE;
    }

    /** 解析座位编号（店名-编号，编号自带店铺，直接查库） */
    private Seat findByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("座位编号不能为空");
        }
        int idx = code.lastIndexOf('-');
        if (idx <= 0 || idx == code.length() - 1) {
            throw new IllegalArgumentException("座位编号格式不正确，应为 店名-编号");
        }
        String storeName = code.substring(0, idx);
        String seatNo = code.substring(idx + 1);
        Seat seat = seatRepository.findByCode(storeName, seatNo);
        if (seat == null) {
            throw new IllegalArgumentException("座位不存在");
        }
        return seat;
    }

    private SeatResponse toResponse(Seat seat, boolean withQr) {
        String qrContent = null;
        String qrBase64 = null;
        if (withQr) {
            qrContent = properties.getQrBaseUrl() + "/?seat="
                    + URLEncoder.encode(seat.code(), StandardCharsets.UTF_8);
            qrBase64 = qrCodeGenerator.generateBase64(qrContent, 280, 280);
        }
        return SeatResponse.from(seat.getStatus(), seat.getId(), seat.getStoreId(),
                seat.getStoreName(), seat.getSeatNo(), seat.getCapacity(),
                seat.getAssignedUserId(), seat.getAssignedGuestId(),
                seat.getAssignedAt(), seat.getOccupiedAt(),
                qrContent, qrBase64);
    }
}
