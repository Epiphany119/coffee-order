package com.coffee.module.auth.biz.application.service;

import com.coffee.module.auth.api.AuthService;
import com.coffee.module.auth.api.dto.*;
import com.coffee.module.auth.biz.domain.PasswordResetToken;
import com.coffee.module.auth.biz.domain.User;
import com.coffee.module.auth.biz.domain.repository.PasswordResetTokenRepository;
import com.coffee.module.auth.biz.domain.repository.UserEmailRepository;
import com.coffee.module.auth.biz.domain.repository.UserRepository;
import com.coffee.module.auth.biz.domain.service.PasswordValidator;
import com.coffee.module.auth.biz.infra.security.PasswordEncoder;
import com.coffee.common.core.exception.ServiceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 认证应用服务
 * <p>
 * 安全特性：
 * 1. 注册：BCrypt(12) 哈希存储密码
 * 2. 登录：验证 BCrypt 哈希；旧明文密码自动升级为 BCrypt
 * 3. 忘记密码：生成 30 分钟有效的一次性令牌，令牌交由站外找回渠道发送
 * 4. 重置密码：令牌校验后允许设置新密码，旧令牌即时作废
 */
@Service
public class AuthApplicationService implements AuthService {

    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\-\\s]{6,30}$");

    private final UserRepository userRepository;
    private final UserEmailRepository userEmailRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailVerificationService emailVerificationService;

    @org.springframework.beans.factory.annotation.Value("${coffee.auth.max-email-bindings:3}")
    private int maxEmailBindings;

    public AuthApplicationService(UserRepository userRepository,
                                  UserEmailRepository userEmailRepository,
                                  PasswordResetTokenRepository tokenRepository,
                                  EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.userEmailRepository = userEmailRepository;
        this.tokenRepository = tokenRepository;
        this.emailVerificationService = emailVerificationService;
    }

    // ======================== 注册 ========================

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request == null) return AuthResponse.fail("请求不能为空");
        String username = request.getUsername();
        String rawPassword = request.getPassword();

        if (username == null || username.isBlank()) {
            return AuthResponse.fail("用户名不能为空");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            return AuthResponse.fail("密码不能为空");
        }
        try {
            PasswordValidator.validate(rawPassword);
        } catch (IllegalArgumentException e) {
            return AuthResponse.fail(e.getMessage());
        }
        if (userRepository.existsByUsername(username)) {
            return AuthResponse.fail("用户名已存在");
        }

        String hash = PasswordEncoder.encode(rawPassword);
        User user = User.register(username, hash, request.getNickname());
        userRepository.save(user);
        return toResponse(user);
    }

    // ======================== 登录 ========================

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        if (request == null) return AuthResponse.fail("用户名或密码错误");
        if (request.getUsername() == null || request.getPassword() == null) {
            return AuthResponse.fail("用户名或密码错误");
        }

        String identifier = request.getUsername().trim();
        if (identifier.isEmpty()) return AuthResponse.fail("用户名或密码错误");

        // “账号”输入框同时支持用户名、系统账号号码和已绑定邮箱。
        User user = userRepository.findByUsername(identifier);
        if (user == null) {
            user = userRepository.findByAccountNo(identifier.toLowerCase(Locale.ROOT));
        }
        if (user == null && identifier.contains("@")) {
            try {
                user = userRepository.findByEmail(emailVerificationService.normalizeEmail(identifier));
            } catch (ServiceException ignored) {
                // 统一返回用户名或密码错误，避免通过登录接口探测邮箱格式和账号是否存在。
            }
        }
        if (user == null) {
            return AuthResponse.fail("用户名或密码错误");
        }

        boolean matched = verifyPassword(request.getPassword(), user);
        if (!matched) {
            return AuthResponse.fail("用户名或密码错误");
        }

        return toResponse(user);
    }

    // ======================== 邮箱登录与注册 ========================

    @Override
    public int sendEmailCode(EmailCodeRequest request) {
        if (request == null) {
            throw new ServiceException(400, "请求不能为空");
        }
        if (request.getPurpose() != EmailCodePurpose.LOGIN && request.getPurpose() != EmailCodePurpose.REGISTER) {
            throw new ServiceException(400, "验证码用途无效");
        }
        String email = emailVerificationService.normalizeEmail(request.getEmail());
        if (request.getPurpose() == EmailCodePurpose.REGISTER && userEmailRepository.findByEmail(email) != null) {
            throw new ServiceException(409, "该邮箱已经被绑定，请选择邮箱登录");
        }
        return emailVerificationService.send(email, request.getPurpose());
    }

    @Override
    public EmailAvailabilityResponse checkEmailAvailability(String rawEmail) {
        String email = emailVerificationService.normalizeEmail(rawEmail);
        boolean bound = userEmailRepository.findByEmail(email) != null;
        return new EmailAvailabilityResponse(
                !bound,
                bound,
                bound ? "该邮箱已经被绑定，请选择邮箱登录" : "该邮箱可用，可以继续注册");
    }

    @Override
    @Transactional
    public int sendEmailBindCode(Long userId, String email) {
        if (userId == null || userRepository.findById(userId) == null) {
            throw new ServiceException(404, "用户不存在");
        }
        String normalized = emailVerificationService.normalizeEmail(email);
        userEmailRepository.lockUser(userId);
        if (userEmailRepository.findByEmail(normalized) != null) {
            throw new ServiceException(409, "该邮箱已经被绑定，请更换其他邮箱");
        }
        if (userEmailRepository.countByUserId(userId) >= safeMaxEmailBindings()) {
            throw new ServiceException(409, "每个用户最多绑定 " + safeMaxEmailBindings() + " 个邮箱");
        }
        return emailVerificationService.send(normalized, EmailCodePurpose.BIND);
    }

    @Override
    @Transactional
    public AuthResponse emailLogin(EmailLoginRequest request) {
        if (request == null) return AuthResponse.fail("请求不能为空");
        String email = emailVerificationService.normalizeEmail(request.getEmail());
        if (!emailVerificationService.verify(email, EmailCodePurpose.LOGIN, request.getCode())) {
            return AuthResponse.fail("验证码错误、已过期或已使用，请重新获取");
        }
        User user = userRepository.findByEmail(email);
        return user == null
                ? AuthResponse.fail("该邮箱尚未创建账户，请选择邮箱注册")
                : toResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse emailRegister(EmailRegisterRequest request) {
        if (request == null) return AuthResponse.fail("请求不能为空");
        String email = emailVerificationService.normalizeEmail(request.getEmail());
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        if (username.length() < 2 || username.length() > 50) {
            return AuthResponse.fail("用户名长度需要在2到50个字符之间");
        }
        String rawPassword = request.getPassword();
        if (rawPassword == null || rawPassword.isBlank()) {
            return AuthResponse.fail("密码不能为空");
        }
        try {
            PasswordValidator.validate(rawPassword);
        } catch (IllegalArgumentException ex) {
            return AuthResponse.fail(ex.getMessage());
        }
        if (userEmailRepository.findByEmail(email) != null) {
            return AuthResponse.fail("该邮箱已经被绑定，请选择邮箱登录");
        }
        if (userRepository.existsByUsername(username)) {
            return AuthResponse.fail("用户名已存在");
        }
        if (!emailVerificationService.verify(email, EmailCodePurpose.REGISTER, request.getCode())) {
            return AuthResponse.fail("验证码错误、已过期或已使用，请重新获取");
        }

        User user = User.register(username, PasswordEncoder.encode(rawPassword), request.getNickname());
        userRepository.save(user);
        try {
            userEmailRepository.add(user.getId(), email, true);
        } catch (DataIntegrityViolationException ex) {
            throw new ServiceException(409, "该邮箱已经被绑定，请选择邮箱登录");
        }
        user.setEmail(email);
        user.setEmails(List.of(email));
        return toResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse bindEmail(Long userId, EmailBindRequest request) {
        if (request == null) return AuthResponse.fail("请求不能为空");
        User user = userRepository.findById(userId);
        if (user == null) throw new ServiceException(404, "用户不存在");

        String email = emailVerificationService.normalizeEmail(request.getEmail());
        userEmailRepository.lockUser(userId);
        UserEmailRepository.UserEmailBinding existing = userEmailRepository.findByEmail(email);
        if (existing != null) {
            return AuthResponse.fail(userId.equals(existing.userId())
                    ? "该邮箱已经绑定在当前账户"
                    : "该邮箱已经被绑定其他账户");
        }
        int currentEmailCount = userEmailRepository.countByUserId(userId);
        if (currentEmailCount >= safeMaxEmailBindings()) {
            return AuthResponse.fail("每个用户最多绑定 " + safeMaxEmailBindings() + " 个邮箱");
        }
        if (!emailVerificationService.verify(email, EmailCodePurpose.BIND, request.getCode())) {
            return AuthResponse.fail("验证码错误、已过期或已使用，请重新获取");
        }

        try {
            userEmailRepository.add(userId, email, currentEmailCount == 0);
        } catch (DataIntegrityViolationException ex) {
            throw new ServiceException(409, "该邮箱已经被绑定，请更换其他邮箱");
        }
        if (currentEmailCount == 0) {
            userRepository.updateEmail(userId, email);
        }
        applyEmails(user);
        return toResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse unbindEmail(Long userId, String rawEmail) {
        User user = userRepository.findById(userId);
        if (user == null) throw new ServiceException(404, "用户不存在");
        userEmailRepository.lockUser(userId);
        List<String> emails = userEmailRepository.findEmailsByUserId(userId);
        String email = rawEmail == null || rawEmail.isBlank()
                ? (emails.isEmpty() ? user.getEmail() : emails.get(0))
                : emailVerificationService.normalizeEmail(rawEmail);
        if (email == null || email.isBlank()) {
            return AuthResponse.fail("当前没有已绑定邮箱");
        }
        if (!emails.contains(email)) {
            return AuthResponse.fail("该邮箱未绑定在当前账户");
        }

        userEmailRepository.delete(userId, email);
        List<String> remaining = userEmailRepository.findEmailsByUserId(userId);
        String primary = remaining.isEmpty() ? null : remaining.get(0);
        userEmailRepository.setPrimary(userId, primary);
        userRepository.updateEmail(userId, primary);
        user.setEmails(remaining);
        user.setEmail(primary);
        return toResponse(user);
    }

    /**
     * 验密：先 BCrypt，失败则尝试明文（旧用户），比对成功则自动升级
     */
    private boolean verifyPassword(String rawPassword, User user) {
        String stored = user.getPasswordHash();
        if (stored == null) {
            return false;
        }

        // 1) BCrypt 密文（以 $2a$ / $2b$ / $2y$ 开头）
        if (stored.startsWith("$2")) {
            return PasswordEncoder.matches(rawPassword, stored);
        }

        // 2) 旧明文密码：比对后自动升级
        if (stored.equals(rawPassword)) {
            String newHash = PasswordEncoder.encode(rawPassword);
            userRepository.updatePassword(user.getId(), newHash);
            user.setPasswordHash(newHash);
            return true;
        }

        return false;
    }

    // ======================== 用户信息 ========================

    @Override
    public AuthResponse getUserInfo(Long id) {
        User user = userRepository.findById(id);
        if (user == null) {
            return AuthResponse.fail("用户不存在");
        }
        return toResponse(user);
    }

    // ======================== 个人资料 ========================

    @Override
    @Transactional
    public AuthResponse updateProfile(Long id, UserProfileUpdateRequest request) {
        if (request == null) throw new ServiceException(400, "资料请求不能为空");
        User user = userRepository.findById(id);
        if (user == null) throw new ServiceException(404, "用户不存在");

        String nickname = trim(request.getNickname(), 50);
        String phone = trim(request.getPhone(), 30);
        String wechatId = trim(request.getWechatId(), 80);
        String qqNumber = trim(request.getQqNumber(), 20);
        String otherInfo = trim(request.getOtherInfo(), 500);
        if (phone != null && !PHONE.matcher(phone).matches()) {
            throw new ServiceException(400, "请输入有效的联系电话");
        }
        if (request.getBirthday() != null && request.getBirthday().isAfter(LocalDate.now())) {
            throw new ServiceException(400, "生日不能晚于今天");
        }

        user.setNickname(nickname);
        user.setPhone(phone);
        user.setBirthday(request.getBirthday());
        user.setWechatId(wechatId);
        user.setQqNumber(qqNumber);
        user.setOtherInfo(otherInfo);
        userRepository.updateProfile(user);
        return toResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse updateAvatar(Long id, String avatarUrl) {
        User user = userRepository.findById(id);
        if (user == null) throw new ServiceException(404, "用户不存在");
        String normalized = trim(avatarUrl, 500);
        if (normalized == null || !normalized.startsWith("/uploads/")) {
            throw new ServiceException(400, "头像地址无效");
        }
        user.setAvatarUrl(normalized);
        userRepository.updateAvatar(id, normalized);
        return toResponse(user);
    }

    // ======================== 店铺偏好 ========================

    @Override
    public Long getLastStoreId(Long userId) {
        User user = userRepository.findById(userId);
        return user != null ? user.getLastStoreId() : null;
    }

    @Override
    public void updateLastStore(Long userId, Long storeId) {
        if (userId != null) {
            userRepository.updateLastStore(userId, storeId);
        }
    }

    // ======================== 忘记密码 ========================

    @Override
    @Transactional
    public String forgotPassword(ForgotPasswordRequest request) {
        if (request == null) throw new ServiceException(400, "请求不能为空");
        String username = request.getUsername();
        if (username == null || username.isBlank()) {
            throw new ServiceException(400, "请输入用户名");
        }

        User user = userRepository.findByUsername(username);
        if (user == null) {
            // 不暴露用户是否存在；控制器统一返回成功提示，避免账号枚举。
            return null;
        }

        // 作废该用户之前所有未使用的令牌
        tokenRepository.invalidatePreviousTokens(user.getId());

        // 生成新令牌
        PasswordResetToken token = PasswordResetToken.generate(user.getId());
        tokenRepository.save(token);

        return token.getToken();
    }

    // ======================== 重置密码 ========================

    @Override
    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        if (request == null) return AuthResponse.fail("请求不能为空");
        String tokenStr = request.getToken();
        String newPassword = request.getNewPassword();

        if (tokenStr == null || tokenStr.isBlank()) {
            return AuthResponse.fail("令牌不能为空");
        }
        if (newPassword == null || newPassword.isBlank()) {
            return AuthResponse.fail("新密码不能为空");
        }
        try {
            PasswordValidator.validate(newPassword);
        } catch (IllegalArgumentException e) {
            return AuthResponse.fail(e.getMessage());
        }

        PasswordResetToken resetToken = tokenRepository.findByToken(tokenStr);
        if (resetToken == null || !resetToken.isValid()) {
            return AuthResponse.fail("令牌无效或已过期");
        }

        // 更新密码
        String newHash = PasswordEncoder.encode(newPassword);
        userRepository.updatePassword(resetToken.getUserId(), newHash);

        // 标记令牌已使用
        resetToken.markUsed();
        tokenRepository.save(resetToken);

        // 获取用户信息用于响应
        User user = userRepository.findById(resetToken.getUserId());

        return user == null ? AuthResponse.fail("用户不存在") : toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        AuthResponse response = AuthResponse.ok(user.getId(), user.getUsername(), user.getNickname(),
                user.getTotalSpent() != null ? user.getTotalSpent() : 0.0);
        response.setAccountNo(user.getAccountNo());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setPhone(user.getPhone());
        response.setBirthday(user.getBirthday());
        response.setWechatId(user.getWechatId());
        response.setQqNumber(user.getQqNumber());
        response.setEmail(user.getEmail());
        response.setEmails(user.getEmails());
        response.setOtherInfo(user.getOtherInfo());
        return response;
    }

    private void applyEmails(User user) {
        List<String> emails = userEmailRepository.findEmailsByUserId(user.getId());
        user.setEmails(emails);
        user.setEmail(emails.isEmpty() ? null : emails.get(0));
    }

    private int safeMaxEmailBindings() {
        return Math.max(1, maxEmailBindings);
    }

    private String trim(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > maxLength) {
            throw new ServiceException(400, "资料内容不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }
}
