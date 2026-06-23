package com.shm.core.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁配置（Phase 16）
 *
 * <h3>与现有 RedisConfig 的关系</h3>
 * <p>RedisConfig 提供 StringRedisTemplate / RedisTemplate 用于缓存（Cache-Aside），
 * 本类提供 RedissonClient 用于分布式锁增强（WatchDog + 可重入 + 公平锁）。
 * 两者共享同一个 Redis 实例，通过不同客户端操作。
 *
 * <h3>配置</h3>
 * <ul>
 *   <li>单节点模式（匹配当前单 Redis 部署）</li>
 *   <li>Jackson JSON 编解码（与项目 ObjectMapper SNAKE_CASE 一致）</li>
 *   <li>连接超时 3000ms + 重试 3 次（间隔 1500ms）</li>
 * </ul>
 */
@Configuration
public class RedissonConfig {

    private static final Logger log = LoggerFactory.getLogger(RedissonConfig.class);

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setConnectTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);
        config.setCodec(new JsonJacksonCodec());

        RedissonClient client = Redisson.create(config);
        log.info("Redisson 客户端已创建: redis://{}:{}", redisHost, redisPort);
        return client;
    }
}
