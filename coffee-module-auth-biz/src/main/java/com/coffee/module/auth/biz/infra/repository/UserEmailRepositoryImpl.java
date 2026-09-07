package com.coffee.module.auth.biz.infra.repository;

import com.coffee.module.auth.biz.domain.repository.UserEmailRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 用户邮箱绑定关系的 JDBC 仓储实现。 */
@Repository
public class UserEmailRepositoryImpl implements UserEmailRepository {

    private final JdbcTemplate jdbc;

    public UserEmailRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserEmailBinding findByEmail(String email) {
        List<UserEmailBinding> rows = jdbc.query(
                "SELECT user_id,email,is_primary FROM coffee_user_email WHERE email=? LIMIT 1",
                (rs, rowNum) -> new UserEmailBinding(
                        rs.getLong("user_id"),
                        rs.getString("email"),
                        rs.getBoolean("is_primary")),
                email);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<String> findEmailsByUserId(Long userId) {
        return jdbc.query(
                "SELECT email FROM coffee_user_email WHERE user_id=? ORDER BY is_primary DESC,id ASC",
                (rs, rowNum) -> rs.getString("email"),
                userId);
    }

    @Override
    public int countByUserId(Long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM coffee_user_email WHERE user_id=?",
                Integer.class,
                userId);
        return count == null ? 0 : count;
    }

    @Override
    public void lockUser(Long userId) {
        jdbc.queryForObject("SELECT id FROM coffee_user WHERE id=? FOR UPDATE", Long.class, userId);
    }

    @Override
    public void add(Long userId, String email, boolean primary) {
        jdbc.update("INSERT INTO coffee_user_email (user_id,email,is_primary) VALUES (?,?,?)",
                userId, email, primary ? 1 : 0);
    }

    @Override
    public void delete(Long userId, String email) {
        jdbc.update("DELETE FROM coffee_user_email WHERE user_id=? AND email=?", userId, email);
    }

    @Override
    public void setPrimary(Long userId, String email) {
        jdbc.update("UPDATE coffee_user_email SET is_primary=0 WHERE user_id=?", userId);
        if (email != null && !email.isBlank()) {
            jdbc.update("UPDATE coffee_user_email SET is_primary=1 WHERE user_id=? AND email=?",
                    userId, email);
        }
    }
}
