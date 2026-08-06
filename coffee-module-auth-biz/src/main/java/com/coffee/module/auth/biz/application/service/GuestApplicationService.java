package com.coffee.module.auth.biz.application.service;

import com.coffee.module.auth.api.GuestService;
import com.coffee.module.auth.biz.infra.persistence.GuestMapper;
import com.coffee.module.auth.biz.infra.persistence.GuestPO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 游客会话应用服务
 */
@Service
public class GuestApplicationService implements GuestService {

    private final GuestMapper guestMapper;

    public GuestApplicationService(GuestMapper guestMapper) {
        this.guestMapper = guestMapper;
    }

    @Override
    @Transactional
    public String createGuestSession() {
        String guestId = "g-" + UUID.randomUUID().toString().replace("-", "");
        GuestPO po = new GuestPO();
        po.setGuestId(guestId);
        po.setCreatedAt(LocalDateTime.now());
        guestMapper.insert(po);
        return guestId;
    }
}
