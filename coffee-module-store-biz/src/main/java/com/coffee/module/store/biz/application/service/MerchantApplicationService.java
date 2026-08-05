package com.coffee.module.store.biz.application.service;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.security.PasswordEncoder;
import com.coffee.module.store.api.MerchantService;
import com.coffee.module.store.api.dto.MerchantLoginRequest;
import com.coffee.module.store.api.dto.MerchantRegisterRequest;
import com.coffee.module.store.api.dto.MerchantResponse;
import com.coffee.module.store.api.dto.MerchantStatus;
import com.coffee.module.store.biz.domain.Merchant;
import com.coffee.module.store.biz.domain.repository.MerchantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商家应用服务（注册 / 登录 / 信息）
 * <p>商家编号规则：sj-{时间戳后 6 位}，注册时自动生成，作为登录账号
 */
@Service
public class MerchantApplicationService implements MerchantService {

    private static final String NO_PREFIX = "sj-";

    private final MerchantRepository merchantRepository;

    public MerchantApplicationService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    @Override
    @Transactional
    public MerchantResponse register(MerchantRegisterRequest request) {
        String rawPassword = request.getPassword();

        if (rawPassword == null || rawPassword.isBlank()) {
            return MerchantResponse.fail("密码不能为空");
        }
        if (rawPassword.length() < 6) {
            return MerchantResponse.fail("密码至少 6 位");
        }

        String merchantNo = generateMerchantNo();
        String hash = PasswordEncoder.encode(rawPassword);
        Merchant merchant = new Merchant();
        merchant.setMerchantNo(merchantNo);
        merchant.setUsername(merchantNo); // 兼容旧 username 列，保持非空
        merchant.setPasswordHash(hash);
        merchant.setNickname(request.getNickname());
        merchant.setPhone(request.getPhone());
        merchant.setStatus(MerchantStatus.ACTIVE);
        merchantRepository.save(merchant);

        return MerchantResponse.ok(merchant.getId(), merchant.getMerchantNo(),
                merchant.getNickname(), merchant.getPhone(), merchant.getStatus());
    }

    @Override
    public MerchantResponse login(MerchantLoginRequest request) {
        if (request.getMerchantNo() == null || request.getPassword() == null) {
            return MerchantResponse.fail("商家编号或密码错误");
        }

        Merchant merchant = merchantRepository.findByMerchantNo(request.getMerchantNo().trim())
                .orElse(null);
        if (merchant == null) {
            return MerchantResponse.fail("商家编号或密码错误");
        }
        if (!PasswordEncoder.matches(request.getPassword(), merchant.getPasswordHash())) {
            return MerchantResponse.fail("商家编号或密码错误");
        }
        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            return MerchantResponse.fail("账号已被禁用");
        }

        return MerchantResponse.ok(merchant.getId(), merchant.getMerchantNo(),
                merchant.getNickname(), merchant.getPhone(), merchant.getStatus());
    }

    @Override
    public MerchantResponse getMerchant(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ServiceException(400, "商家不存在"));
        return MerchantResponse.ok(merchant.getId(), merchant.getMerchantNo(),
                merchant.getNickname(), merchant.getPhone(), merchant.getStatus());
    }

    /**
     * 生成商家编号：sj-{时间戳后 6 位}，冲突时自增重试
     */
    private String generateMerchantNo() {
        String millis = String.valueOf(System.currentTimeMillis());
        long suffix = Long.parseLong(millis.substring(millis.length() - 6));
        String no;
        do {
            no = NO_PREFIX + String.format("%06d", suffix);
            suffix = (suffix + 1) % 1_000_000;
        } while (merchantRepository.existsByMerchantNo(no));
        return no;
    }
}
