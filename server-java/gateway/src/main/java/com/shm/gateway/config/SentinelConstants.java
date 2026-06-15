package com.shm.gateway.config;

/**
 * Sentinel 共享常量（Phase 9）
 *
 * <p>集中管理限流响应格式等跨类常量，避免重复定义导致维护不同步。
 */
public final class SentinelConstants {

    private SentinelConstants() {
        // 工具类禁止实例化
    }

    /** 限流 Block 响应 JSON — 与 Node.js code:4006 (RATE_LIMITED) 一致 */
    public static final String BLOCK_RESPONSE =
            "{\"code\":4006,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}";

    /** 敏感 API 资源名 — QPS=10 */
    public static final String RESOURCE_SENSITIVE = "sensitive-api";

    /** 普通 API 资源名 — QPS=60 */
    public static final String RESOURCE_NORMAL = "normal-api";
}
