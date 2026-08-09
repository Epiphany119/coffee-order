package com.coffee.module.store.biz.application.service;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.security.PasswordEncoder;
import com.coffee.module.store.api.MerchantService;
import com.coffee.module.store.api.dto.MerchantLoginRequest;
import com.coffee.module.store.api.dto.MerchantRegisterRequest;
import com.coffee.module.store.api.dto.MerchantResponse;
import com.coffee.module.store.api.dto.MerchantStatus;
import com.coffee.module.store.api.dto.MerchantProfileUpdateRequest;
import com.coffee.module.store.api.dto.MerchantPasswordChangeRequest;
import com.coffee.module.store.api.dto.StoreStatus;
import com.coffee.module.store.biz.domain.Merchant;
import com.coffee.module.store.biz.domain.Store;
import com.coffee.module.store.biz.domain.repository.MerchantRepository;
import com.coffee.module.store.biz.domain.repository.StoreRepository;
import com.coffee.module.store.biz.infra.persistence.UserCredentialMapper;
import com.coffee.module.store.biz.infra.persistence.UserCredentialPO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商家应用服务（注册 / 登录 / 信息）
 * <p>商家编号规则：sj-{时间戳后 6 位}，注册时自动生成，作为登录账号
 * <p>注册流程：提交资料 → 审核 → 分配编号与初始密码。
 * 当前系统审核默认直接通过（注册即 ACTIVE），但流程校验保留：
 * 用户名须为已注册的用户端账号（coffee_user.username，不可重复注册商家），
 * 商家登录密码默认与用户端登录密码一致
 * <p>入驻现有店铺：系统为每家门店预分配占位商家记录（merchant_no 预生成、信息为空、status=DISABLED）。
 * 商家注册时携带 storeId → 激活该店占位记录（填入资料、置 ACTIVE、绑定店铺），
 * 同时回填 coffee_user.merchant_no（一个用户端账号只能绑定一家店）。
 * 注册时未入驻的商家（开新店模式）生成独立档案，之后可开新店
 */
@Service
public class MerchantApplicationService implements MerchantService {

    private static final String NO_PREFIX = "sj-";

    private final MerchantRepository merchantRepository;
    private final StoreRepository storeRepository;
    private final UserCredentialMapper userCredentialMapper;

    public MerchantApplicationService(MerchantRepository merchantRepository,
                                      StoreRepository storeRepository,
                                      UserCredentialMapper userCredentialMapper) {
        this.merchantRepository = merchantRepository;
        this.storeRepository = storeRepository;
        this.userCredentialMapper = userCredentialMapper;
    }

    @Override
    @Transactional
    public MerchantResponse register(MerchantRegisterRequest request) {
        String username = request.getUsername();
        String rawPassword = request.getPassword();

        if (username == null || username.isBlank()) {
            return MerchantResponse.fail("用户名不能为空");
        }
        username = username.trim();
        if (rawPassword == null || rawPassword.isBlank()) {
            return MerchantResponse.fail("密码不能为空");
        }
        if (rawPassword.length() < 6) {
            return MerchantResponse.fail("密码至少 6 位");
        }

        // 校验①：用户名须为已注册的用户端账号
        UserCredentialPO user = userCredentialMapper.selectByUsername(username);
        if (user == null) {
            return MerchantResponse.fail("用户名不存在，请先在用户端注册该账号");
        }
        // 校验②：用户名不可重复注册商家
        if (merchantRepository.existsByUsername(username)) {
            return MerchantResponse.fail("该用户名已注册过商家");
        }
        // 校验③：密码须与用户端登录密码一致（商家密码默认沿用用户端密码）
        if (!PasswordEncoder.matches(rawPassword, user.getPassword())) {
            return MerchantResponse.fail("密码与用户端登录密码不一致");
        }

        Long storeId = request.getStoreId();
        if (storeId != null) {
            // 入驻现有店铺：激活该店预分配的占位商家记录并绑定
            return joinExistingStore(request, user, storeId);
        }

        // 开新店模式：生成独立商家档案（未绑定店铺）
        String merchantNo = generateMerchantNo();
        Merchant merchant = new Merchant();
        merchant.setMerchantNo(merchantNo);
        merchant.setUsername(username);
        // 直接沿用用户端密码哈希：商家登录密码 = 用户端登录密码
        merchant.setPasswordHash(user.getPassword());
        merchant.setNickname(request.getNickname());
        merchant.setPhone(request.getPhone());
        // 审核默认直接通过（正常流程为提交资料后审核，此处简化但保留状态字段）
        merchant.setStatus(MerchantStatus.ACTIVE);
        merchantRepository.save(merchant);

        return MerchantResponse.ok(merchant.getId(), merchant.getMerchantNo(),
                merchant.getNickname(), merchant.getPhone(), merchant.getStatus(), null);
    }

    /**
     * 入驻现有店铺：激活该店预分配的占位商家记录，绑定店铺并回填用户端 merchant_no
     * <p>占位记录：系统初始化时为每家门店预生成的商家档案（merchant_no 预分配、username/password 为空、status=DISABLED）。
     * 入驻即填入真实资料并置 ACTIVE，商家以预分配的 merchant_no 作为登录账号
     */
    @Transactional
    protected MerchantResponse joinExistingStore(MerchantRegisterRequest request,
                                                 UserCredentialPO user, Long storeId) {
        String username = user.getUsername();
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ServiceException(400, "店铺不存在"));
        if (store.getMerchantId() != null) {
            throw new ServiceException(400, "该店铺已被其他商家入驻");
        }
        Merchant placed = merchantRepository.findByStoreId(storeId)
                .orElseThrow(() -> new ServiceException(400, "该店铺未初始化商家档案，请联系管理员"));
        if (placed.getUsername() != null) {
            throw new ServiceException(400, "该店铺商家档案已激活，无法重复入驻");
        }
        // 用户端账号已绑定过商家（一账号一店）
        if (user.getMerchantNo() != null) {
            throw new ServiceException(400, "该用户已绑定商家 " + user.getMerchantNo() + "，一个用户端账号只能入驻一家店");
        }
        // 该用户端账号可能已注册过商家档案（注册时未选店，走的是开新店路径）。
        // 该档案尚未绑定店铺（无任何业务数据），废弃删除以免与占位记录的 username 唯一索引冲突。
        merchantRepository.deleteByUsername(username);

        // 激活占位记录：填入资料 + 绑定店铺
        placed.setUsername(username);
        placed.setPasswordHash(user.getPassword());
        placed.setNickname(request.getNickname());
        placed.setPhone(request.getPhone());
        placed.setStatus(MerchantStatus.ACTIVE);
        placed.setStoreId(storeId);
        placed.setStoreName(store.getName());
        merchantRepository.save(placed);

        // 新入驻默认打烊：店铺营业状态由商家在后台手动切换（点击营业/打烊）
        store.setMerchantId(placed.getId());
        store.setStatus(StoreStatus.CLOSED);
        storeRepository.save(store);

        // 回填用户端账号绑定的商家编号（容错：表层面记录绑定关系）
        userCredentialMapper.updateMerchantNo(user.getId(), placed.getMerchantNo());

        return MerchantResponse.ok(placed.getId(), placed.getMerchantNo(),
                placed.getNickname(), placed.getPhone(), placed.getStatus(), placed.getStoreName());
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
        // 占位记录（未入驻）无密码，登录一律拒绝
        if (merchant.getPasswordHash() == null
                || !PasswordEncoder.matches(request.getPassword(), merchant.getPasswordHash())) {
            return MerchantResponse.fail("商家编号或密码错误");
        }
        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            return MerchantResponse.fail("账号已被禁用");
        }

        return MerchantResponse.ok(merchant.getId(), merchant.getMerchantNo(),
                merchant.getNickname(), merchant.getPhone(), merchant.getStatus(), merchant.getStoreName());
    }

    @Override
    public MerchantResponse getMerchant(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ServiceException(400, "商家不存在"));
        return MerchantResponse.ok(merchant.getId(), merchant.getMerchantNo(),
                merchant.getNickname(), merchant.getPhone(), merchant.getStatus(), merchant.getStoreName());
    }

    @Override
    @Transactional
    public MerchantResponse updateProfile(Long merchantId, MerchantProfileUpdateRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ServiceException(404, "商家不存在"));
        String nickname = request == null || request.getNickname() == null ? "" : request.getNickname().trim();
        String phone = request == null || request.getPhone() == null ? "" : request.getPhone().trim();
        if (nickname.length() > 50) throw new ServiceException(400, "昵称不能超过 50 个字符");
        if (phone.length() > 20) throw new ServiceException(400, "联系电话不能超过 20 个字符");
        merchant.setNickname(nickname.isEmpty() ? null : nickname);
        merchant.setPhone(phone.isEmpty() ? null : phone);
        merchantRepository.save(merchant);
        return MerchantResponse.ok(merchant.getId(), merchant.getMerchantNo(), merchant.getNickname(),
                merchant.getPhone(), merchant.getStatus(), merchant.getStoreName());
    }

    @Override
    @Transactional
    public void changePassword(Long merchantId, MerchantPasswordChangeRequest request) {
        if (request == null || request.getOldPassword() == null || request.getNewPassword() == null) {
            throw new ServiceException(400, "请填写当前密码和新密码");
        }
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ServiceException(404, "商家不存在"));
        if (!PasswordEncoder.matches(request.getOldPassword(), merchant.getPasswordHash())) {
            throw new ServiceException(400, "当前密码不正确");
        }
        String next = request.getNewPassword();
        if (next.length() < 8 || !next.matches(".*[A-Za-z].*") || !next.matches(".*\\d.*")) {
            throw new ServiceException(400, "新密码至少 8 位，且须包含字母和数字");
        }
        if (PasswordEncoder.matches(next, merchant.getPasswordHash())) {
            throw new ServiceException(400, "新密码不能与当前密码相同");
        }
        merchant.setPasswordHash(PasswordEncoder.encode(next));
        merchantRepository.save(merchant);
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
