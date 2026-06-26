package com.shm.core.config;

import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sentinel 系统自适应规则（Phase 17）
 *
 * <p>在服务启动后加载系统级保护规则，当服务器过载时自动拒绝请求。
 * 规则值可根据部署环境通过 Nacos 动态调整，此处为默认值。
 *
 * <h3>规则说明</h3>
 * <ul>
 *   <li>入口 QPS ≤ 200 — 超过则拒绝（保护服务不被突发流量冲垮）</li>
 *   <li>系统 LOAD ≤ 4.0 — CPU 过高时自动降级</li>
 *   <li>平均 RT ≤ 100ms — 响应变慢时自动降级</li>
 *   <li>线程数 ≤ 200 — 防止线程耗尽</li>
 * </ul>
 */
@Component
public class SentinelSystemRuleConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SentinelSystemRuleConfig.class);

    @Override
    public void run(String... args) {
        SystemRule rule = new SystemRule();
        rule.setHighestSystemLoad(4.0);
        rule.setAvgRt(100);
        rule.setMaxThread(200);
        rule.setQps(200);

        SystemRuleManager.loadRules(List.of(rule));
        log.info("[Sentinel System] 系统自适应规则已加载: LOAD≤4.0, RT≤100ms, Thread≤200, QPS≤200");
    }
}
