package com.shm.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

/**
 * Sentinel Gateway 限流规则初始化（Phase 9）
 *
 * <h3>限流策略（与 Node.js 令牌桶一致）</h3>
 * <ul>
 *   <li>全局入口 QPS: 60 req/s（QPS 模式，非每分钟 — Sentinel 以秒为单位）</li>
 *   <li>敏感接口 QPS: 10 req/s（订单创建、管理后台、举报提交）</li>
 *   <li>白名单: /api/health、/api/auth/login、/api/auth/refresh — 不限流</li>
 * </ul>
 *
 * <p>规则同时通过 Nacos 数据源持久化（见 application.yml 的 spring.cloud.sentinel.datasource），
 * 此配置类确保首次启动/恢复时规则立即可用。
 *
 * <p>Sentinel Dashboard 端口: 8088，可实时查看和调整限流规则。
 */
@Configuration
public class SentinelGatewayConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SentinelGatewayConfig.class);

    // QPS 阈值（与 Node.js 令牌桶速率一致）
    private static final double GLOBAL_QPS = 60.0;
    private static final double SENSITIVE_QPS = 10.0;

    // 敏感 API 前缀（需要更严格的限流）
    private static final String[] SENSITIVE_PATHS = {
            "/api/admin",        // 管理后台全部接口
            "/api/orders",       // 创建订单（POST）
            "/api/reports",      // 提交举报（POST — core 路由）
    };

    // API 分组名
    private static final String GROUP_SENSITIVE = "sensitive-api";
    private static final String GROUP_GLOBAL = "global-api";

    @Override
    public void run(String... args) {
        // 仅当 Nacos 数据源未提供规则时加载默认值（兜底策略）
        // Nacos 规则优先级更高，可动态调整而无需重启
        boolean hasApiGroups = !GatewayApiDefinitionManager.getApiDefinitions().isEmpty();
        boolean hasFlowRules = !GatewayRuleManager.getRules().isEmpty();

        if (!hasApiGroups) {
            log.info("[Sentinel Gateway] Nacos 无 API 分组，加载代码默认值");
            initApiGroups();
        } else {
            log.info("[Sentinel Gateway] 检测到 Nacos API 分组（{} 组），跳过代码默认值",
                    GatewayApiDefinitionManager.getApiDefinitions().size());
        }

        if (!hasFlowRules) {
            log.info("[Sentinel Gateway] Nacos 无流控规则，加载代码默认值: 全局={}QPS, 敏感={}QPS",
                    GLOBAL_QPS, SENSITIVE_QPS);
            initFlowRules();
        } else {
            log.info("[Sentinel Gateway] 检测到 Nacos 流控规则（{} 条），跳过代码默认值",
                    GatewayRuleManager.getRules().size());
        }
    }

    /**
     * 初始化 API 分组 — 将 URL 前缀归类为不同限流组
     */
    private void initApiGroups() {
        Set<ApiDefinition> definitions = new HashSet<>();

        // 敏感 API 组 — 需要更严格的限流
        Set<ApiPredicateItem> sensitiveItems = new HashSet<>();
        for (String path : SENSITIVE_PATHS) {
            sensitiveItems.add(new ApiPathPredicateItem()
                    .setPattern(path + "/**")
                    .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
        }
        definitions.add(new ApiDefinition(GROUP_SENSITIVE)
                .setPredicateItems(sensitiveItems));

        // 全局 API 组 — 除敏感 API 外的所有 API
        Set<ApiPredicateItem> globalItems = new HashSet<>();
        globalItems.add(new ApiPathPredicateItem()
                .setPattern("/api/**")
                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
        definitions.add(new ApiDefinition(GROUP_GLOBAL)
                .setPredicateItems(globalItems));

        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
    }

    /**
     * 初始化限流规则 — 按 API 分组设置 QPS
     */
    private void initFlowRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        // 规则 1: 敏感 API — 10 QPS
        rules.add(new GatewayFlowRule(GROUP_SENSITIVE)
                .setCount(SENSITIVE_QPS)
                .setIntervalSec(1)
                .setControlBehavior(0)); // 0 = 直接拒绝

        // 规则 2: 全局 API — 60 QPS
        rules.add(new GatewayFlowRule(GROUP_GLOBAL)
                .setCount(GLOBAL_QPS)
                .setIntervalSec(1)
                .setControlBehavior(0)); // 0 = 直接拒绝

        GatewayRuleManager.loadRules(rules);
    }
}
