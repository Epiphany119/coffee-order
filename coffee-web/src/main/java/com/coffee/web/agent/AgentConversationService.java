package com.coffee.web.agent;

import com.coffee.common.core.exception.ServiceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** 对话历史持久化；会话 ID 只能在同一个经认证的身份范围中继续使用。 */
@Service
public class AgentConversationService {
    private final JdbcTemplate jdbc;

    public AgentConversationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String ensureSession(String suppliedSessionId, String ownerKey, String scene) {
        bootstrap();
        if (suppliedSessionId != null && !suppliedSessionId.isBlank()) {
            Integer exists = jdbc.queryForObject("SELECT COUNT(1) FROM agent_conversation WHERE session_id=? AND owner_key=? AND scene=?",
                    Integer.class, suppliedSessionId, ownerKey, scene);
            if (exists != null && exists > 0) return suppliedSessionId;
            throw new ServiceException(403, "无权继续该 Agent 会话");
        }
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO agent_conversation(session_id,owner_key,scene,created_at,updated_at) VALUES (?,?,?,?,?)",
                sessionId, ownerKey, scene, LocalDateTime.now(), LocalDateTime.now());
        return sessionId;
    }

    public void append(String sessionId, String ownerKey, String role, String content) {
        if (content == null || content.isBlank()) return;
        bootstrap();
        jdbc.update("INSERT INTO agent_conversation_message(session_id,owner_key,role,content,created_at) VALUES (?,?,?,?,?)",
                sessionId, ownerKey, role, content, LocalDateTime.now());
        jdbc.update("UPDATE agent_conversation SET updated_at=? WHERE session_id=? AND owner_key=?", LocalDateTime.now(), sessionId, ownerKey);
    }

    public List<String> recent(String sessionId, String ownerKey, int size) {
        bootstrap();
        List<String> newestFirst = jdbc.query("""
                SELECT CONCAT(role, '：', content) FROM agent_conversation_message
                WHERE session_id=? AND owner_key=? ORDER BY id DESC LIMIT ?
                """, (rs, n) -> rs.getString(1), sessionId, ownerKey, Math.max(1, Math.min(size, 12)))
                ;
        List<String> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return chronological;
    }

    private void bootstrap() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS agent_conversation (
                  session_id VARCHAR(40) PRIMARY KEY, owner_key VARCHAR(80) NOT NULL,
                  scene VARCHAR(40) NOT NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
                  KEY idx_agent_conversation_owner (owner_key, scene, updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS agent_conversation_message (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT, session_id VARCHAR(40) NOT NULL,
                  owner_key VARCHAR(80) NOT NULL, role VARCHAR(16) NOT NULL, content TEXT NOT NULL,
                  created_at DATETIME NOT NULL, KEY idx_agent_message_session (session_id, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }
}
