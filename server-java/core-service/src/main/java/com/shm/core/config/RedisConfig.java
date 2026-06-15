package com.shm.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 基础设施配置（Phase 10）
 *
 * <h3>序列化</h3>
 * <p>使用 GenericJackson2JsonRedisSerializer，存入 Redis 的 JSON 保持
 * SNAKE_CASE 字段名（与 API 响应一致），反序列化时自动映射到 camelCase 实体。
 *
 * <h3>缓存策略</h3>
 * <p>不使用 Spring 声明式缓存（@Cacheable/@CacheEvict），
 * 而是通过 Service 层手动 Cache-Aside 模式（StringRedisTemplate）实现，
 * 以获得更好的细粒度控制和降级能力。
 */
@Configuration
public class RedisConfig {

    private final ObjectMapper objectMapper;

    public RedisConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // RedisTemplate — 手动 Cache-Aside 基础设施
    // ============================================================

    /** RedisTemplate — 用于缓存对象（key=String, value=Object JSON） */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key 序列化为 String
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 序列化为 JSON（使用项目 ObjectMapper 的 SNAKE_CASE 配置）
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /** StringRedisTemplate — 用于计数器、锁、Cache-Aside 缓存等简单操作 */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
