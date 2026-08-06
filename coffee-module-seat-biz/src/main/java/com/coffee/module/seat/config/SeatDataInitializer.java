package com.coffee.module.seat.config;

import com.coffee.module.seat.api.dto.SeatStatus;
import com.coffee.module.seat.biz.domain.Seat;
import com.coffee.module.seat.biz.domain.repository.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 座位数据初始化：
 * 1. 初始化座位模板表 seat_template（99 桌定义，全店共用）：
 *    70 双人桌 + 20 四人桌 + 9 多人桌，编号 001 起
 * 2. 为所有营业中（OPEN）店铺按模板补齐座位实例（store_id + template_id 映射 + 状态）
 */
@Component
public class SeatDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeatDataInitializer.class);

    private final SeatRepository seatRepository;
    private final SeatProperties properties;
    private final JdbcTemplate jdbcTemplate;

    public SeatDataInitializer(SeatRepository seatRepository, SeatProperties properties,
                               JdbcTemplate jdbcTemplate) {
        this.seatRepository = seatRepository;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        initTemplate();
        initStoreSeats();
    }

    /** 座位模板为空时按配置生成 99 桌定义（双人 70 / 四人 20 / 多人 9） */
    private void initTemplate() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM seat_template", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        List<Object[]> rows = new ArrayList<>();
        int seq = 0;
        seq = appendTemplateRows(rows, seq, properties.getTwoSeats(), 2, "双人桌");
        seq = appendTemplateRows(rows, seq, properties.getFourSeats(), 4, "四人桌");
        appendTemplateRows(rows, seq, properties.getMultiSeats(), 8, "多人桌");
        jdbcTemplate.batchUpdate(
                "INSERT INTO seat_template (seat_no, capacity, type_name) VALUES (?, ?, ?)", rows);
        log.info("[seat] 已初始化座位模板 {} 桌（{} 双人桌 / {} 四人桌 / {} 多人桌）",
                rows.size(), properties.getTwoSeats(),
                properties.getFourSeats(), properties.getMultiSeats());
    }

    private int appendTemplateRows(List<Object[]> rows, int startId, int count, int capacity, String typeName) {
        for (int i = 1; i <= count; i++) {
            rows.add(new Object[]{String.format("%03d", startId + i), capacity, typeName});
        }
        return startId + count;
    }

    /** 为无座位的店铺按模板补座（不区分营业状态：每家店固定座位表，营业与否只影响用户端选店） */
    private void initStoreSeats() {
        List<Map<String, Object>> stores = jdbcTemplate.queryForList(
                "SELECT id FROM store ORDER BY id ASC");
        if (stores.isEmpty()) {
            log.warn("[seat] 无店铺，跳过座位实例初始化");
            return;
        }
        List<Map<String, Object>> templates = jdbcTemplate.queryForList(
                "SELECT id, seat_no, capacity FROM seat_template ORDER BY id ASC");
        if (templates.isEmpty()) {
            log.warn("[seat] 座位模板为空，跳过座位实例初始化");
            return;
        }

        for (Map<String, Object> store : stores) {
            long storeId = ((Number) store.get("id")).longValue();
            if (seatRepository.countByStoreId(storeId) > 0) {
                continue; // 该店已有座位实例
            }
            List<Seat> seats = new ArrayList<>();
            for (Map<String, Object> tpl : templates) {
                Seat seat = new Seat();
                seat.setStoreId(storeId);
                seat.setTemplateId(((Number) tpl.get("id")).longValue());
                seat.setSeatNo((String) tpl.get("seat_no"));
                seat.setCapacity(((Number) tpl.get("capacity")).intValue());
                seat.setStatus(SeatStatus.FREE);
                seats.add(seat);
            }
            seatRepository.saveAll(seats);
            log.info("[seat] 已按模板为店铺 id={} 补齐 {} 个座位实例", storeId, seats.size());
        }
    }
}
