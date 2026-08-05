package com.coffee.module.seat.config;

import com.coffee.module.seat.api.dto.SeatStatus;
import com.coffee.module.seat.biz.domain.Seat;
import com.coffee.module.seat.biz.domain.repository.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 座位数据初始化：首次启动时按配置生成店铺全部座位
 * （默认 70 双人桌 + 20 四人桌 + 9 多人桌 = 99 桌）
 */
@Component
public class SeatDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeatDataInitializer.class);

    private final SeatRepository seatRepository;
    private final SeatProperties properties;

    public SeatDataInitializer(SeatRepository seatRepository, SeatProperties properties) {
        this.seatRepository = seatRepository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seatRepository.count() > 0) {
            return;
        }

        String storeName = properties.getStoreName();
        List<Seat> seats = new ArrayList<>();
        int seq = 0;

        seq = appendSeats(seats, storeName, seq, properties.getTwoSeats(), 2, "双人桌");
        seq = appendSeats(seats, storeName, seq, properties.getFourSeats(), 4, "四人桌");
        appendSeats(seats, storeName, seq, properties.getMultiSeats(), 8, "多人桌");

        seatRepository.saveAll(seats);

        log.info("[seat] 已初始化 {} 个座位（{} {} 双人桌 / {} 四人桌 / {} 多人桌）",
                seats.size(), storeName, properties.getTwoSeats(),
                properties.getFourSeats(), properties.getMultiSeats());
    }

    private int appendSeats(List<Seat> seats, String storeName, int startId,
                            int count, int capacity, String typeLabel) {
        for (int i = 1; i <= count; i++) {
            Seat seat = new Seat();
            seat.setStoreName(storeName);
            seat.setSeatNo(String.format("%03d", startId + i));
            seat.setCapacity(capacity);
            seat.setStatus(SeatStatus.FREE);
            seats.add(seat);
        }
        log.info("[seat] 已生成 {} {} 张：编号 {} - {}",
                typeLabel, count,
                String.format("%03d", startId + 1), String.format("%03d", startId + count));
        return startId + count;
    }
}
