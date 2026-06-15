package com.shm.common.constant;

/**
 * Redis Key 前缀常量（Phase 10）
 *
 * <p>统一管理所有缓存 Key，避免散布魔术字符串。
 * <p>命名规范：{@code shm:<模块>:<用途>}
 */
public final class RedisKeys {

    private RedisKeys() {
        // 工具类禁止实例化
    }

    // ============================================================
    // 缓存
    // ============================================================

    /** 商品列表缓存 — key: {@code shm:product:list:<page>:<pageSize>:<hash>} */
    public static final String PRODUCT_LIST = "shm:product:list";

    /** 用户公开信息缓存 — key: {@code shm:user:public:<userId>} */
    public static final String USER_PUBLIC = "shm:user:public";

    // ============================================================
    // 安全
    // ============================================================

    /** Token 黑名单前缀 — per-user key: {@code shm:token:blacklist:<userId>}，TTL 7 天 */
    public static final String TOKEN_BLACKLIST = "shm:token:blacklist";

    // ============================================================
    // 分布式锁
    // ============================================================

    /** 下单锁 — key: {@code shm:lock:order:<buyerId>:<productId>}，TTL 30s */
    public static final String LOCK_ORDER = "shm:lock:order";

    // ============================================================
    // 缓存空值（防穿透）
    // ============================================================

    /** 空值缓存前缀 — key: {@code shm:empty:<entity>:<id>}，TTL 60s */
    public static final String EMPTY_PREFIX = "shm:empty";

    // ============================================================
    // 工具方法
    // ============================================================

    /** 构建下单锁 key */
    public static String orderLockKey(Long buyerId, Long productId) {
        return LOCK_ORDER + ":" + buyerId + ":" + productId;
    }

    /** 构建 Token 黑名单 per-user key（独立 TTL，自动过期） */
    public static String tokenBlacklistKey(Long userId) {
        return TOKEN_BLACKLIST + ":" + userId;
    }

    /** 构建用户公开信息缓存 key */
    public static String userPublicKey(Long userId) {
        return USER_PUBLIC + ":" + userId;
    }

    /** 构建空值缓存 key */
    public static String emptyKey(String entity, Object id) {
        return EMPTY_PREFIX + ":" + entity + ":" + id;
    }
}
