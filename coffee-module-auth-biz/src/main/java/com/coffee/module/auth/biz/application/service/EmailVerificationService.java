package com.coffee.module.auth.biz.application.service;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.auth.api.dto.EmailCodePurpose;
import com.coffee.module.auth.biz.infra.repository.EmailVerificationCodeRepository;
import com.coffee.module.auth.biz.infra.repository.EmailVerificationRateLimitRepository;
import com.coffee.module.auth.biz.infra.security.PasswordEncoder;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * QQ SMTP 邮箱验证码服务。
 *
 * <p>验证码 BCrypt 哈希双写到 Redis 和 MySQL：Redis 提供快速读取，MySQL 在 Redis
 * 重启或数据丢失时兜底。使用、过期或达到错误次数上限后会从两处清除，不留下可重放验证码。</p>
 */
@Service
public class EmailVerificationService {
    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,190}$");
    private static final Pattern CODE = Pattern.compile("^\\d{6}$");

    private final EmailVerificationCodeRepository codes;
    private final EmailVerificationRateLimitRepository rateLimits;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final SecureRandom random = new SecureRandom();

    @Value("${spring.mail.username:}")
    private String sender;

    @Value("${coffee.auth.email-code.expires-seconds:300}")
    private int expiresSeconds;

    @Value("${coffee.auth.email-code.cooldown-seconds:60}")
    private int cooldownSeconds;

    @Value("${coffee.auth.email-code.max-attempts:5}")
    private int maxAttempts;

    @Value("${coffee.auth.email-code.window-seconds:600}")
    private int requestWindowSeconds;

    @Value("${coffee.auth.email-code.max-requests-per-window:5}")
    private int maxRequestsPerWindow;

    @Value("${coffee.auth.email-code.lock-seconds:600}")
    private int lockSeconds;

    public EmailVerificationService(EmailVerificationCodeRepository codes,
                                    EmailVerificationRateLimitRepository rateLimits,
                                    ObjectProvider<JavaMailSender> mailSenderProvider,
                                    ObjectProvider<StringRedisTemplate> redisProvider) {
        this.codes = codes;
        this.rateLimits = rateLimits;
        this.mailSenderProvider = mailSenderProvider;
        this.redisProvider = redisProvider;
    }

    /**
     * @return 成功发送后再次请求前需等待的秒数
     */
    public int send(String rawEmail, EmailCodePurpose purpose) {
        String email = normalizeEmail(rawEmail);
        if (purpose == null) {
            throw new ServiceException(400, "请选择验证码用途");
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || sender == null || sender.isBlank()) {
            throw new ServiceException(503, "邮箱服务尚未配置，请联系管理员");
        }

        int safeCooldownSeconds = Math.max(30, cooldownSeconds);
        int safeWindowSeconds = Math.max(60, requestWindowSeconds);
        int safeMaxRequests = Math.max(1, maxRequestsPerWindow);
        int safeLockSeconds = Math.max(60, lockSeconds);
        EmailVerificationRateLimitRepository.SendReservation reservation = rateLimits.reserve(
                email, safeCooldownSeconds, safeWindowSeconds, safeMaxRequests, safeLockSeconds);
        if (reservation.status() == EmailVerificationRateLimitRepository.Status.COOLDOWN) {
            throw new ServiceException(429, "请 " + reservation.waitSeconds() + " 秒后再获取验证码");
        }
        if (reservation.status() == EmailVerificationRateLimitRepository.Status.LOCKED) {
            throw new ServiceException(429, "请求过于频繁，该邮箱已冷却 " + reservation.waitSeconds() + " 秒");
        }

        int safeExpiresSeconds = Math.max(60, expiresSeconds);
        String code = String.format("%06d", random.nextInt(1_000_000));
        String codeHash = PasswordEncoder.encode(code);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(safeExpiresSeconds);
        codes.save(email, purpose.name(), codeHash, expiresAt);
        cacheCodeHash(email, purpose, codeHash, safeExpiresSeconds);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // 同时设置纯文本和 HTML 正文时必须启用 multipart 模式，避免邮件发送前直接抛出
            // "Not in multipart mode"，并让不同邮箱客户端能够选择合适的正文格式。
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(sender);
            helper.setTo(email);
            helper.setSubject("FIKA · " + actionName(purpose) + "验证码");
            helper.setText(plainText(purpose, code, safeExpiresSeconds), htmlText(purpose, code, safeExpiresSeconds));
            mailSender.send(message);
        } catch (Exception ex) {
            cleanup(email, purpose);
            rateLimits.releaseCooldown(email);
            Throwable root = ex;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            log.error("邮箱验证码发送失败: purpose={}, senderDomain={}, recipientDomain={}, errorType={}, rootType={}, rootMessage={}",
                    purpose, domainOf(sender), domainOf(email), ex.getClass().getSimpleName(),
                    root.getClass().getSimpleName(), root.getMessage(), ex);
            throw new ServiceException(502, "验证码邮件发送失败，请稍后再试");
        }
        return safeCooldownSeconds;
    }

    /** 验证成功后删除 Redis 和 MySQL 中的临时验证码，不能重放。 */
    public boolean verify(String rawEmail, EmailCodePurpose purpose, String rawCode) {
        if (purpose == null || rawCode == null || !CODE.matcher(rawCode.trim()).matches()) {
            return false;
        }
        String email = normalizeEmail(rawEmail);
        EmailVerificationCodeRepository.EmailCodeRecord record = codes.find(email, purpose.name()).orElse(null);
        if (record == null) {
            evictCodeHash(email, purpose);
            return false;
        }
        if (record.expiresAt() == null || !record.expiresAt().isAfter(LocalDateTime.now())
                || record.attempts() >= Math.max(1, maxAttempts)) {
            cleanup(email, purpose);
            return false;
        }

        // 缓存值必须与 MySQL 当前哈希一致；旧缓存或 Redis 数据丢失都会回退到数据库。
        String cachedHash = cachedCodeHash(email, purpose);
        String hash = record.codeHash().equals(cachedHash) ? cachedHash : record.codeHash();
        if (!PasswordEncoder.matches(rawCode.trim(), hash)) {
            codes.recordFailedAttempt(record.id());
            if (record.attempts() + 1 >= Math.max(1, maxAttempts)) {
                cleanup(email, purpose);
            }
            return false;
        }

        boolean consumed = codes.consume(record.id(), Math.max(1, maxAttempts));
        if (consumed) {
            evictCodeHash(email, purpose);
        }
        return consumed;
    }

    /** 每分钟清理 MySQL 里已到期的验证码，并同步删除对应 Redis key。 */
    @Scheduled(fixedDelayString = "${coffee.auth.email-code.cleanup-interval-ms:60000}")
    public void purgeExpiredCodes() {
        for (EmailVerificationCodeRepository.ExpiredCodeKey key : codes.findExpired(500)) {
            if (codes.deleteIfExpired(key.id())) {
                try {
                    evictCodeHash(key.email(), EmailCodePurpose.valueOf(key.purpose()));
                } catch (IllegalArgumentException ex) {
                    // 防御历史脏数据：MySQL 临时记录已删除，Redis 最多等 TTL 自行失效。
                    log.warn("清理到未知用途的邮箱验证码临时记录: {}", key.purpose());
                }
            }
        }
        rateLimits.purgeOldStates();
    }

    public String normalizeEmail(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw new ServiceException(400, "请输入有效的邮箱地址");
        }
        return email;
    }

    private void cleanup(String email, EmailCodePurpose purpose) {
        codes.delete(email, purpose.name());
        evictCodeHash(email, purpose);
    }

    private void cacheCodeHash(String email, EmailCodePurpose purpose, String codeHash, int ttlSeconds) {
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis != null) {
                redis.opsForValue().set(cacheKey(email, purpose), codeHash, Duration.ofSeconds(ttlSeconds));
            }
        } catch (RuntimeException ex) {
            log.debug("Redis 暂不可用，邮箱验证码将仅使用 MySQL 兜底校验");
        }
    }

    private String cachedCodeHash(String email, EmailCodePurpose purpose) {
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            return redis == null ? null : redis.opsForValue().get(cacheKey(email, purpose));
        } catch (RuntimeException ex) {
            log.debug("Redis 暂不可用，邮箱验证码改用 MySQL 兜底校验");
            return null;
        }
    }

    private void evictCodeHash(String email, EmailCodePurpose purpose) {
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis != null) {
                redis.delete(cacheKey(email, purpose));
            }
        } catch (RuntimeException ex) {
            log.debug("Redis 暂不可用，验证码缓存将在 TTL 后自行失效");
        }
    }

    /** Redis key 只存 email 的 SHA-256 指纹，避免把邮箱地址暴露在缓存键中。 */
    private String cacheKey(String email, EmailCodePurpose purpose) {
        return "fika:auth:email-code:" + purpose.name().toLowerCase(Locale.ROOT) + ":" + emailFingerprint(email);
    }

    private String emailFingerprint(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    /** 仅记录域名，避免日志泄露邮箱地址。 */
    private String domainOf(String email) {
        if (email == null || email.isBlank()) return "<empty>";
        int at = email.lastIndexOf('@');
        return at > 0 && at < email.length() - 1 ? email.substring(at + 1) : "<invalid>";
    }

    private String actionName(EmailCodePurpose purpose) {
        return switch (purpose) {
            case REGISTER -> "创建会员账户";
            case BIND -> "绑定邮箱";
            case PASSWORD_CHANGE -> "更新登录密码";
            case LOGIN -> "登录";
        };
    }

    private String plainText(EmailCodePurpose purpose, String code, int ttlSeconds) {
        return "FIKA 邮箱验证码\n\n"
                + "你正在" + actionName(purpose) + " FIKA。\n"
                + "验证码：" + code + "\n"
                + "有效期：" + Math.max(1, ttlSeconds / 60) + " 分钟。\n\n"
                + "请勿将验证码透露给他人。若不是你本人操作，可忽略此邮件。\n\nFIKA 咖啡与轻食";
    }

    /** 表格布局和内联样式可兼容 QQ、Outlook、Apple Mail 等常见邮件客户端。 */
    private String htmlText(EmailCodePurpose purpose, String code, int ttlSeconds) {
        String action = actionName(purpose);
        int minutes = Math.max(1, ttlSeconds / 60);
        return """
                <!doctype html>
                <html lang="zh-CN">
                <body style="margin:0;padding:0;background:#f4f5f2;color:#21342c;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#f4f5f2;padding:36px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:100%%;max-width:600px;background:#ffffff;border-radius:20px;overflow:hidden;box-shadow:0 10px 30px rgba(17,57,45,.10);">
                        <tr><td style="padding:30px 36px;background:#143b2e;color:#ffffff;">
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"><tr>
                            <td style="font-size:22px;font-weight:700;letter-spacing:.08em;">FIKA</td>
                            <td align="right" style="font-size:12px;letter-spacing:.12em;color:#cce0d4;">COFFEE · BAKERY · MOMENTS</td>
                          </tr></table>
                        </td></tr>
                        <tr><td style="padding:38px 36px 26px;">
                          <p style="margin:0 0 10px;font-size:13px;font-weight:700;letter-spacing:.12em;color:#d9703d;">SECURITY CODE</p>
                          <h1 style="margin:0;font-size:28px;line-height:1.35;color:#173a2e;">验证你的身份</h1>
                          <p style="margin:14px 0 28px;font-size:15px;line-height:1.8;color:#63736b;">你正在%s FIKA。请在页面中输入下方验证码以继续。</p>
                          <div style="padding:23px 16px;border:1px solid #dbe9df;border-radius:14px;background:#f6faf7;text-align:center;">
                            <span style="display:block;font-size:12px;letter-spacing:.08em;color:#769083;">YOUR VERIFICATION CODE</span>
                            <strong style="display:block;margin-top:10px;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:34px;line-height:1;color:#173a2e;letter-spacing:.28em;">%s</strong>
                          </div>
                          <p style="margin:22px 0 0;font-size:13px;line-height:1.7;color:#63736b;">验证码将在 <strong style="color:#173a2e;">%d 分钟</strong> 后失效。为了保护你的账户，请勿将验证码告诉任何人。</p>
                        </td></tr>
                        <tr><td style="padding:18px 36px 30px;">
                          <div style="padding:13px 15px;border-left:3px solid #e58a58;background:#fff8f3;font-size:12px;line-height:1.7;color:#896650;">不是你本人发起的操作？你无需采取任何行动，本邮件可以直接忽略。</div>
                        </td></tr>
                        <tr><td style="padding:20px 36px;background:#f8f9f7;border-top:1px solid #edf0ec;font-size:11px;line-height:1.7;color:#98a29c;">此邮件由 FIKA 自动发送，请勿直接回复。<br>慢下来，享受每一杯咖啡。</td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(action, code, minutes);
    }
}
