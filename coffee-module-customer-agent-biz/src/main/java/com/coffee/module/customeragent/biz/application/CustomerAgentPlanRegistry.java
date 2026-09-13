package com.coffee.module.customeragent.biz.application;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.customeragent.api.dto.AgentOrderLine;
import com.coffee.module.customeragent.api.dto.AgentOrderPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * 短时、一次性方案仓库。确认时只接受令牌，商品行始终从服务端 JSON 快照读取。
 * 集群部署时应替换为 Redis 实现，并沿用相同的身份绑定和原子消费语义。
 */
@Component
public class CustomerAgentPlanRegistry {
    private static final Duration TTL = Duration.ofMinutes(5);
    /** 方案最多包含 10 行商品，每行数量仍受单独限制。 */
    private static final int MAX_PLAN_LINES = 10;
    private static final Set<String> ALLOWED_SIZES = Set.of("SMALL", "MEDIUM", "LARGE", "CUSTOM");
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{16,128}");
    private final ConcurrentHashMap<String, Entry> plans = new ConcurrentHashMap<>();
    private final ObjectMapper jsonMapper;

    public CustomerAgentPlanRegistry(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String issue(AgentOrderPlan plan) {
        purgeExpired();
        validatePlan(plan);
        final String planJson;
        try {
            planJson = jsonMapper.writeValueAsString(plan);
        } catch (Exception ex) {
            throw new ServiceException(500, "Agent 方案保存失败");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        plans.put(token, new Entry(planJson, Instant.now().plus(TTL), new AtomicReference<>()));
        return token;
    }

    public AgentOrderPlan consume(String token, String idempotencyKey, Long storeId, Long userId, String guestId, boolean includeAddOn) {
        validateIdempotencyKey(idempotencyKey);
        Entry entry = requireEntry(token);
        AgentOrderPlan resolvedPlan = resolveEntry(entry, storeId, userId, guestId, includeAddOn);
        // includeAddOn 会改变最终商品行，必须纳入同一确认声明，避免同一幂等键复用成另一份订单。
        String confirmationClaim = idempotencyKey + "|" + includeAddOn;
        String owner = entry.confirmationClaim().get();
        if (owner == null && !entry.confirmationClaim().compareAndSet(null, confirmationClaim)) {
            owner = entry.confirmationClaim().get();
        }
        if (owner != null && !owner.equals(confirmationClaim)) {
            throw new ServiceException(409, "该 Agent 方案已确认，请勿重复下单");
        }
        return resolvedPlan;
    }

    /** 读取用户选中的方案但不抢占幂等声明，供最终商品/库存校验使用。 */
    public AgentOrderPlan resolve(String token, Long storeId, Long userId, String guestId, boolean includeAddOn) {
        return resolveEntry(requireEntry(token), storeId, userId, guestId, includeAddOn);
    }

    private AgentOrderPlan resolveEntry(Entry entry, Long storeId, Long userId, String guestId, boolean includeAddOn) {
        final AgentOrderPlan plan;
        try {
            plan = jsonMapper.readValue(entry.planJson(), AgentOrderPlan.class);
        } catch (Exception ex) {
            throw new ServiceException(500, "Agent 方案状态损坏，请重新生成");
        }
        if (!Objects.equals(plan.storeId(), storeId) || !same(plan.userId(), userId) || !same(plan.guestId(), guestId)) {
            throw new ServiceException(403, "该 Agent 方案不属于当前门店或身份");
        }
        List<AgentOrderLine> resolvedItems = plan.resolvedItems(includeAddOn);
        validateLines(resolvedItems);
        return new AgentOrderPlan(plan.storeId(), plan.userId(), plan.guestId(), resolvedItems, List.of(), plan.note());
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new ServiceException(400, "订单幂等键格式无效");
        }
    }

    private Entry requireEntry(String token) {
        if (token == null || !token.matches("[a-f0-9]{32}")) {
            throw new ServiceException(400, "无效的 Agent 方案确认令牌");
        }
        Entry entry = plans.get(token);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            throw new ServiceException(410, "该 Agent 方案已过期，请重新生成");
        }
        return entry;
    }

    private boolean same(Object left, Object right) { return left == null ? right == null : left.equals(right); }
    private void purgeExpired() { if (plans.size() > 200) plans.entrySet().removeIf(e -> Instant.now().isAfter(e.getValue().expiresAt())); }

    private void validatePlan(AgentOrderPlan plan) {
        if (plan == null || plan.storeId() == null || plan.storeId() <= 0) {
            throw new ServiceException(400, "Agent 方案门店无效");
        }
        boolean hasUser = plan.userId() != null && plan.userId() > 0;
        boolean hasGuest = plan.guestId() != null && !plan.guestId().isBlank();
        if (hasUser == hasGuest) {
            throw new ServiceException(400, "Agent 方案身份无效");
        }
        validateLines(plan.items());
        if (!plan.addOnItems().isEmpty()) validateLines(plan.addOnItems());
    }

    private void validateLines(List<AgentOrderLine> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > MAX_PLAN_LINES) {
            throw new ServiceException(400, "Agent 方案内容异常");
        }
        for (AgentOrderLine item : lines) {
            if (item == null || item.productCode() == null || item.productCode().isBlank()
                    || item.productCode().length() > 80 || item.quantity() < 1 || item.quantity() > 9
                    || item.productId() == null || item.productId() <= 0
                    || item.productName() == null || item.productName().isBlank() || item.productName().length() > 200
                    || item.size() == null || !ALLOWED_SIZES.contains(item.size().toUpperCase(Locale.ROOT))) {
                throw new ServiceException(400, "Agent 方案商品校验失败");
            }
        }
    }

    private record Entry(String planJson, Instant expiresAt, AtomicReference<String> confirmationClaim) { }
}
