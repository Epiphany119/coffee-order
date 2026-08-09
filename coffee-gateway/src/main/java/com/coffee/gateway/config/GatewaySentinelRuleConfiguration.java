package com.coffee.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

/**
 * 网关本地兜底规则。生产环境可将同名规则迁移到 Nacos，规则发布不再需要重启。
 */
@Configuration
public class GatewaySentinelRuleConfiguration {
    @PostConstruct
    public void loadFallbackRules() {
        ApiDefinition orderApi = new ApiDefinition("fika-order-api")
                .setPredicateItems(Set.of(new ApiPathPredicateItem()
                        .setPattern("/api/orders")
                        .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX)));
        GatewayApiDefinitionManager.loadApiDefinitions(Set.of(orderApi));
        GatewayFlowRule orderRule = new GatewayFlowRule("fika-order-api")
                .setCount(80)
                .setIntervalSec(1);
        GatewayRuleManager.loadRules(new HashSet<>(Set.of(orderRule)));
    }
}
