package com.coffee.module.seat.biz.infra.repository;

import com.coffee.module.seat.api.dto.SeatStatus;
import com.coffee.module.seat.biz.domain.Seat;
import com.coffee.module.seat.biz.domain.repository.SeatRepository;
import com.coffee.module.seat.biz.infra.persistence.SeatMapper;
import com.coffee.module.seat.biz.infra.persistence.SeatPO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 座位仓储实现
 */
@Repository
public class SeatRepositoryImpl implements SeatRepository {

    private final SeatMapper seatMapper;

    public SeatRepositoryImpl(SeatMapper seatMapper) {
        this.seatMapper = seatMapper;
    }

    @Override
    public List<Seat> findFreeSeats(Long storeId, int requiredCapacity, int limit) {
        return seatMapper.selectFreeSeats(storeId, requiredCapacity, limit).stream()
                .map(this::toSeat)
                .collect(Collectors.toList());
    }

    @Override
    public long countByStoreId(Long storeId) {
        return seatMapper.countByStoreId(storeId);
    }

    @Override
    public boolean tryAssign(Long seatId, Long userId, String guestId) {
        return seatMapper.updateAssign(seatId, userId, guestId) > 0;
    }

    @Override
    public boolean occupy(Long seatId, Long userId, String guestId) {
        return seatMapper.updateOccupy(seatId, userId, guestId) > 0;
    }

    @Override
    public boolean leave(Long seatId, Long userId, String guestId) {
        return seatMapper.updateLeave(seatId, userId, guestId) > 0;
    }

    @Override
    public int releaseExpired(LocalDateTime before) {
        return seatMapper.updateReleaseExpired(before);
    }

    @Override
    public Seat findById(Long seatId) {
        SeatPO po = seatMapper.selectByIdWithJoin(seatId);
        return po == null ? null : toSeat(po);
    }

    @Override
    public Seat findByCode(String storeName, String seatNo) {
        SeatPO po = seatMapper.selectByCode(storeName, seatNo);
        return po == null ? null : toSeat(po);
    }

    @Override
    public List<Seat> findAll(Long storeId) {
        return seatMapper.selectListByStore(storeId).stream()
                .map(this::toSeat).collect(Collectors.toList());
    }

    @Override
    public List<Seat> findOccupiedByStoreAndIdentity(Long storeId, Long userId, String guestId) {
        return seatMapper.selectOccupiedByStoreAndIdentity(storeId, userId, guestId).stream()
                .map(this::toSeat).collect(Collectors.toList());
    }

    @Override
    public long count() {
        return seatMapper.selectCount(null);
    }

    @Override
    public void saveAll(List<Seat> seats) {
        List<SeatPO> pos = seats.stream().map(this::toPO).collect(Collectors.toList());
        for (SeatPO po : pos) {
            seatMapper.insert(po);
        }
    }

    private Seat toSeat(SeatPO po) {
        Seat s = new Seat();
        s.setId(po.getId());
        s.setStoreId(po.getStoreId());
        s.setTemplateId(po.getTemplateId());
        s.setStoreName(po.getStoreName());
        s.setSeatNo(po.getSeatNo());
        s.setCapacity(po.getCapacity());
        s.setStatus(po.getStatus());
        s.setAssignedUserId(po.getAssignedUserId());
        s.setAssignedGuestId(po.getAssignedGuestId());
        s.setAssignedAt(po.getAssignedAt());
        s.setOccupiedAt(po.getOccupiedAt());
        return s;
    }

    private SeatPO toPO(Seat s) {
        SeatPO po = new SeatPO();
        po.setId(s.getId());
        po.setStoreId(s.getStoreId());
        po.setTemplateId(s.getTemplateId());
        po.setStatus(s.getStatus() == null ? SeatStatus.FREE : s.getStatus());
        po.setAssignedUserId(s.getAssignedUserId());
        po.setAssignedGuestId(s.getAssignedGuestId());
        po.setAssignedAt(s.getAssignedAt());
        po.setOccupiedAt(s.getOccupiedAt());
        return po;
    }
}
