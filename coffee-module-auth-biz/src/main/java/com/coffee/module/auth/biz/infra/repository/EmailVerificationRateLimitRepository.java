package com.coffee.module.auth.biz.infra.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** 邮箱验证码发送限流的 MySQL 权威状态；Redis 不可用时仍然有效。 */
@Repository
public class EmailVerificationRateLimitRepository {
    private final JdbcTemplate jdbc;

    public EmailVerificationRateLimitRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 允许每次发送之间至少间隔 cooldownSeconds；窗口内第六次请求会进入 lockSeconds 的冷却。
     * SELECT ... FOR UPDATE 保证同一邮箱的并发请求不会绕过限制。
     */
    @Transactional
    public SendReservation reserve(String email, int cooldownSeconds,
                                   int windowSeconds, int maxRequests, int lockSeconds) {
        jdbc.update("""
                INSERT IGNORE INTO email_verification_rate_limit
                (email,next_send_at,window_started_at,send_count,lock_until,created_at,updated_at)
                VALUES (?,NOW(),NOW(),0,NULL,NOW(),NOW())
                """, email);

        RateLimitState state = findForUpdate(email);
        LocalDateTime now = LocalDateTime.now();
        if (state.lockUntil() != null && state.lockUntil().isAfter(now)) {
            return SendReservation.locked(waitSeconds(now, state.lockUntil()));
        }
        if (state.nextSendAt() != null && state.nextSendAt().isAfter(now)) {
            return SendReservation.cooldown(waitSeconds(now, state.nextSendAt()));
        }

        boolean newWindow = state.windowStartedAt() == null
                || !state.windowStartedAt().plusSeconds(windowSeconds).isAfter(now);
        int requestsInWindow = newWindow ? 0 : state.sendCount();
        if (requestsInWindow >= maxRequests) {
            LocalDateTime lockUntil = now.plusSeconds(lockSeconds);
            jdbc.update("""
                    UPDATE email_verification_rate_limit
                    SET lock_until=?, next_send_at=?, updated_at=NOW()
                    WHERE email=?
                    """, lockUntil, lockUntil, email);
            return SendReservation.locked(lockSeconds);
        }

        jdbc.update("""
                UPDATE email_verification_rate_limit
                SET next_send_at=?, window_started_at=?, send_count=?, lock_until=NULL, updated_at=NOW()
                WHERE email=?
                """, now.plusSeconds(cooldownSeconds), newWindow ? now : state.windowStartedAt(),
                requestsInWindow + 1, email);
        return SendReservation.accepted();
    }

    /** 邮件服务器故障时允许用户修复配置后立即重试，但保留窗口计数防止滥用。 */
    public void releaseCooldown(String email) {
        jdbc.update("""
                UPDATE email_verification_rate_limit
                SET next_send_at=NOW(), updated_at=NOW()
                WHERE email=?
                """, email);
    }

    public void purgeOldStates() {
        jdbc.update("DELETE FROM email_verification_rate_limit WHERE updated_at < DATE_SUB(NOW(), INTERVAL 1 DAY)");
    }

    private RateLimitState findForUpdate(String email) {
        List<RateLimitState> states = jdbc.query("""
                        SELECT next_send_at,window_started_at,send_count,lock_until
                        FROM email_verification_rate_limit
                        WHERE email=?
                        FOR UPDATE
                        """,
                (resultSet, rowNumber) -> new RateLimitState(
                        time(resultSet.getTimestamp("next_send_at")),
                        time(resultSet.getTimestamp("window_started_at")),
                        resultSet.getInt("send_count"),
                        time(resultSet.getTimestamp("lock_until"))),
                email);
        if (states.isEmpty()) {
            throw new IllegalStateException("邮箱验证码限流状态初始化失败");
        }
        return states.get(0);
    }

    private LocalDateTime time(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private long waitSeconds(LocalDateTime now, LocalDateTime until) {
        return Math.max(1, Duration.between(now, until).getSeconds() + 1);
    }

    private record RateLimitState(LocalDateTime nextSendAt, LocalDateTime windowStartedAt,
                                  int sendCount, LocalDateTime lockUntil) {
    }

    public record SendReservation(Status status, long waitSeconds) {
        public static SendReservation accepted() { return new SendReservation(Status.ACCEPTED, 0); }
        public static SendReservation cooldown(long seconds) { return new SendReservation(Status.COOLDOWN, seconds); }
        public static SendReservation locked(long seconds) { return new SendReservation(Status.LOCKED, seconds); }
    }

    public enum Status { ACCEPTED, COOLDOWN, LOCKED }
}
