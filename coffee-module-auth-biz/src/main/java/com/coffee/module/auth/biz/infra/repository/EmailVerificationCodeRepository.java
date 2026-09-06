package com.coffee.module.auth.biz.infra.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** MySQL 中只短暂保存邮箱验证码哈希，用于 Redis 丢失时的校验兜底。 */
@Repository
public class EmailVerificationCodeRepository {
    private final JdbcTemplate jdbc;

    public EmailVerificationCodeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(String email, String purpose, String codeHash, LocalDateTime expiresAt) {
        jdbc.update("""
                INSERT INTO email_verification_code
                (email,purpose,code_hash,expires_at,attempts,created_at,updated_at)
                VALUES (?,?,?,?,0,NOW(),NOW())
                ON DUPLICATE KEY UPDATE
                code_hash=?, expires_at=?, attempts=0, updated_at=NOW()
                """, email, purpose, codeHash, expiresAt, codeHash, expiresAt);
    }

    public Optional<EmailCodeRecord> find(String email, String purpose) {
        List<EmailCodeRecord> records = jdbc.query("""
                        SELECT id,email,purpose,code_hash,expires_at,attempts
                        FROM email_verification_code
                        WHERE email=? AND purpose=?
                        LIMIT 1
                        """,
                (resultSet, rowNumber) -> new EmailCodeRecord(
                        resultSet.getLong("id"),
                        resultSet.getString("email"),
                        resultSet.getString("purpose"),
                        resultSet.getString("code_hash"),
                        toLocalDateTime(resultSet.getTimestamp("expires_at")),
                        resultSet.getInt("attempts")),
                email, purpose);
        return records.stream().findFirst();
    }

    /** 校验成功后直接删除临时验证码，确保不能重放。 */
    public boolean consume(long id, int maxAttempts) {
        return jdbc.update("""
                DELETE FROM email_verification_code
                WHERE id=? AND expires_at > NOW() AND attempts < ?
                """, id, maxAttempts) == 1;
    }

    public void recordFailedAttempt(long id) {
        jdbc.update("""
                UPDATE email_verification_code
                SET attempts=attempts+1, updated_at=NOW()
                WHERE id=?
                """, id);
    }

    public void delete(String email, String purpose) {
        jdbc.update("DELETE FROM email_verification_code WHERE email=? AND purpose=?", email, purpose);
    }

    public List<ExpiredCodeKey> findExpired(int limit) {
        return jdbc.query("""
                        SELECT id,email,purpose FROM email_verification_code
                        WHERE expires_at <= NOW()
                        ORDER BY expires_at ASC
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> new ExpiredCodeKey(
                        resultSet.getLong("id"),
                        resultSet.getString("email"),
                        resultSet.getString("purpose")),
                Math.max(1, Math.min(limit, 1_000)));
    }

    public boolean deleteIfExpired(long id) {
        return jdbc.update("DELETE FROM email_verification_code WHERE id=? AND expires_at <= NOW()", id) == 1;
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public record EmailCodeRecord(long id, String email, String purpose, String codeHash,
                                  LocalDateTime expiresAt, int attempts) {
    }

    public record ExpiredCodeKey(long id, String email, String purpose) {
    }
}
