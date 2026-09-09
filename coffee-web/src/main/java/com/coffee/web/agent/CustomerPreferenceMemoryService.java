package com.coffee.web.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 顾客侧 Agent 的轻量长期记忆。
 *
 * <p>只保存经过规则提取的偏好，不把原始对话无限复制到提示词中；原始对话仍由
 * {@link AgentConversationService} 按身份隔离保存。偏好表不可用时自动降级为空记忆，
 * 不阻断用户点单。</p>
 */
@Service
public class CustomerPreferenceMemoryService {
    private static final Logger log = LoggerFactory.getLogger(CustomerPreferenceMemoryService.class);
    private static final Pattern BUDGET = Pattern.compile("(?:预算|不超过|以内|左右|最多)\\s*[¥￥]?\\s*(\\d+(?:\\.\\d+)?)");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public CustomerPreferenceMemoryService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Memory remember(String ownerKey, Long storeId, String message) {
        if (ownerKey == null || ownerKey.isBlank() || storeId == null || storeId <= 0) {
            return Memory.empty();
        }
        Map<String, Object> extracted = extract(message);
        if (extracted.isEmpty()) return read(ownerKey, storeId);
        try {
            Map<String, Object> merged = readJson(ownerKey, storeId);
            merged.putAll(extracted);
            LocalDateTime now = LocalDateTime.now();
            String value = json.writeValueAsString(merged);
            jdbc.update("""
                    INSERT INTO agent_customer_preference(owner_key,store_id,preference_json,last_message_at,created_at,updated_at)
                    VALUES (?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE preference_json=VALUES(preference_json),last_message_at=VALUES(last_message_at),updated_at=VALUES(updated_at)
                    """, ownerKey, storeId, value, now, now, now);
            return toMemory(merged);
        } catch (Exception ex) {
            log.warn("customer preference memory skipped: {}", ex.getClass().getSimpleName());
            return Memory.empty();
        }
    }

    public Memory read(String ownerKey, Long storeId) {
        if (ownerKey == null || ownerKey.isBlank() || storeId == null || storeId <= 0) {
            return Memory.empty();
        }
        try {
            List<String> values = jdbc.query(
                    "SELECT preference_json FROM agent_customer_preference WHERE owner_key=? AND store_id=?",
                    (rs, rowNum) -> rs.getString(1), ownerKey, storeId);
            if (values.isEmpty()) return Memory.empty();
            return toMemory(json.readValue(values.get(0), new TypeReference<Map<String, Object>>() { }));
        } catch (DataAccessException ex) {
            // 首次部署尚未执行迁移时，记忆是可选能力，不能让用户侧 Agent 整体不可用。
            log.debug("customer preference memory unavailable: {}", ex.getClass().getSimpleName());
            return Memory.empty();
        } catch (Exception ex) {
            log.warn("customer preference memory parse failed: {}", ex.getClass().getSimpleName());
            return Memory.empty();
        }
    }

    private Map<String, Object> readJson(String ownerKey, Long storeId) {
        try {
            List<String> values = jdbc.query(
                    "SELECT preference_json FROM agent_customer_preference WHERE owner_key=? AND store_id=?",
                    (rs, rowNum) -> rs.getString(1), ownerKey, storeId);
            if (values.isEmpty()) return new LinkedHashMap<>();
            return json.readValue(values.get(0), new TypeReference<Map<String, Object>>() { });
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> extract(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        String text = message == null ? "" : message.trim().toLowerCase();
        if (text.isBlank()) return result;
        if (contains(text, "冰", "冷", "冷饮")) result.put("temperature", "COLD");
        else if (contains(text, "热", "暖", "热饮")) result.put("temperature", "HOT");
        if (contains(text, "无糖", "不加糖", "不甜", "低糖", "少糖")) result.put("sweetness", "LOW");
        else if (contains(text, "全糖", "正常糖", "甜一点")) result.put("sweetness", "NORMAL");
        if (contains(text, "不苦", "别太苦", "清爽", "清淡")) result.put("taste", "LIGHT");
        else if (contains(text, "浓郁", "提神", "苦一点", "醇厚")) result.put("taste", "STRONG");
        if (contains(text, "咖啡", "拿铁", "美式", "浓缩")) result.put("category", "COFFEE");
        else if (contains(text, "奶茶", "茶", "果茶")) result.put("category", "TEA");
        if (contains(text, "甜点", "蛋糕", "小食", "轻食", "汉堡")) result.put("pairing", "FOOD");
        Matcher matcher = BUDGET.matcher(text);
        if (matcher.find()) result.put("budget", matcher.group(1));
        return result;
    }

    private Memory toMemory(Map<String, Object> values) {
        if (values == null || values.isEmpty()) return Memory.empty();
        List<String> signals = new ArrayList<>();
        label(values, signals, "temperature", Map.of("COLD", "偏好冰饮", "HOT", "偏好热饮"));
        label(values, signals, "sweetness", Map.of("LOW", "偏好低糖", "NORMAL", "接受正常甜度"));
        label(values, signals, "taste", Map.of("LIGHT", "偏好清爽不苦", "STRONG", "偏好浓郁提神"));
        label(values, signals, "category", Map.of("COFFEE", "常探索咖啡", "TEA", "常探索茶饮"));
        label(values, signals, "pairing", Map.of("FOOD", "常搭配轻食或甜点"));
        Object budget = values.get("budget");
        if (budget != null) signals.add("最近预算约 ¥" + budget);
        return new Memory(new LinkedHashMap<>(values), List.copyOf(signals));
    }

    private void label(Map<String, Object> values, List<String> signals, String key, Map<String, String> labels) {
        Object value = values.get(key);
        if (value != null && labels.containsKey(String.valueOf(value))) signals.add(labels.get(String.valueOf(value)));
    }

    private boolean contains(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    public record Memory(Map<String, Object> values, List<String> signals) {
        public static Memory empty() { return new Memory(Map.of(), List.of()); }

        public String promptContext() {
            return signals.isEmpty() ? "暂无已记录的口味偏好" : String.join("、", signals);
        }
    }
}
