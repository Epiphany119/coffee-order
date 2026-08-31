package com.coffee.module.customeragent.biz.application;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.customeragent.api.dto.AgentOrderLine;
import com.coffee.module.customeragent.api.dto.AgentOrderPlan;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * 短时、一次性方案仓库。确认时只接受令牌，商品行始终从服务端方案快照读取。
 * 集群部署时应替换为 Redis 实现，并沿用相同的身份绑定和原子消费语义。
 */
@Component
public class CustomerAgentPlanRegistry {
    private static final Duration TTL = Duration.ofMinutes(5);
    /** 原方案最多两项，凑单后允许追加一项；仍限制在小型、安全的点单方案范围内。 */
    private static final int MAX_PLAN_LINES = 10;
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{16,128}");
    private final ConcurrentHashMap<String, Entry> plans = new ConcurrentHashMap<>();

    public String issue(AgentOrderPlan plan) {
        purgeExpired();
        String token = UUID.randomUUID().toString().replace("-", "");
        plans.put(token, new Entry(plan, Instant.now().plus(TTL), new AtomicReference<>()));
        return token;
    }

    public AgentOrderPlan consume(String token, String idempotencyKey, Long storeId, Long userId, String guestId, boolean includeAddOn) {
        if (token == null || !token.matches("[a-f0-9]{32}")) throw new ServiceException(400, "无效的 Agent 方案确认令牌");
        if (idempotencyKey == null || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new ServiceException(400, "订单幂等键格式无效");
        }
        Entry entry = plans.get(token);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) throw new ServiceException(410, "该 Agent 方案已过期，请重新生成");
        AgentOrderPlan plan = entry.plan();
        if (!plan.storeId().equals(storeId) || !same(plan.userId(), userId) || !same(plan.guestId(), guestId)) {
            throw new ServiceException(403, "该 Agent 方案不属于当前门店或身份");
        }
        List<AgentOrderLine> resolvedItems = plan.resolvedItems(includeAddOn);
        if (resolvedItems == null || resolvedItems.isEmpty() || resolvedItems.size() > MAX_PLAN_LINES) throw new ServiceException(400, "Agent 方案内容异常");
        for (AgentOrderLine item : resolvedItems) {
            if (item.productCode() == null || item.productCode().isBlank() || item.quantity() < 1 || item.quantity() > 9) {
                throw new ServiceException(400, "Agent 方案商品校验失败");
            }
        }
        String owner = entry.idempotencyKey().get();
        if (owner == null && !entry.idempotencyKey().compareAndSet(null, idempotencyKey)) owner = entry.idempotencyKey().get();
        if (owner != null && !owner.equals(idempotencyKey)) throw new ServiceException(409, "该 Agent 方案已确认，请勿重复下单");
        return new AgentOrderPlan(plan.storeId(), plan.userId(), plan.guestId(), resolvedItems, List.of(), plan.note());
    }

    private boolean same(Object left, Object right) { return left == null ? right == null : left.equals(right); }
    private void purgeExpired() { if (plans.size() > 200) plans.entrySet().removeIf(e -> Instant.now().isAfter(e.getValue().expiresAt())); }
    private record Entry(AgentOrderPlan plan, Instant expiresAt, AtomicReference<String> idempotencyKey) { }
}
