package com.shm.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 消息驱动配置（Phase 14）
 *
 * <h3>架构</h3>
 * <p>rocketmq-spring-boot-starter 2.3.0 自动配置 RocketMQTemplate / 消费者容器。
 * 本类作为条件开关，仅在 {@code rocketmq.enabled=true} 时激活。
 *
 * <h3>配置来源</h3>
 * <ul>
 *   <li>application.yml — rocketmq.name-server / producer.group / consumer.group</li>
 *   <li>rocketmq-spring-boot-starter 自动配置 RocketMQTemplate</li>
 * </ul>
 *
 * <h3>开发环境</h3>
 * <p>{@code rocketmq.enabled=false} 时本配置不生效，所有 @RocketMQMessageListener
 * 和 Publisher Bean 均不会被创建，服务正常启动。
 */
@Configuration
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = false)
public class RocketMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RocketMQConfig.class);

    public RocketMQConfig() {
        log.info("RocketMQ 消息驱动配置已加载");
    }
}
