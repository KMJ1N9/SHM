package com.shm.common.constant;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 缓存 TTL 常量与工具方法（Phase 10 P2-1 / P2-2）
 *
 * <h3>TTL 设计</h3>
 * <ul>
 *   <li>商品列表缓存 — 5 分钟 ± 120s 抖动，写操作后主动清除（SCAN）</li>
 *   <li>用户公开信息缓存 — 10 分钟 ± 120s 抖动，资料编辑后主动清除</li>
 *   <li>空值缓存 — 60s，统一防穿透短 TTL</li>
 *   <li>Token 黑名单 — 7 天，覆盖 JWT Refresh Token 最大有效期</li>
 * </ul>
 *
 * <h3>抖动策略</h3>
 * <p>所有缓存 TTL 使用双向 ± 随机偏移（{@link #cacheTtlWithJitter}），
 * 避免大量 key 同时过期引发缓存雪崩。偏移量在 {@code [0, jitterMax)} 内均匀分布，
 * 正向/负向各 50% 概率，下限不低于 60s。
 *
 * @see RedisKeys
 */
public final class CacheConstants {

    private CacheConstants() {
        // 工具类禁止实例化
    }

    // ============================================================
    // TTL 常量
    // ============================================================

    /** 商品列表缓存 TTL 基础值（秒） */
    public static final long PRODUCT_LIST_TTL_SECONDS = 300; // 5 分钟

    /** 商品列表缓存抖动范围（秒） */
    public static final int PRODUCT_LIST_JITTER_SECONDS = 120;

    /** 用户公开信息缓存 TTL 基础值（秒） */
    public static final long USER_PUBLIC_TTL_SECONDS = 600; // 10 分钟

    /** 用户公开信息缓存抖动范围（秒） */
    public static final int USER_PUBLIC_JITTER_SECONDS = 120;

    /** 空值缓存 TTL（秒），防穿透 */
    public static final long EMPTY_VALUE_TTL_SECONDS = 60;

    /** Token 黑名单条目 TTL（秒），覆盖 JWT Refresh Token 最大有效期 */
    public static final long TOKEN_BLACKLIST_TTL_SECONDS = 604800; // 7 天

    // ============================================================
    // 工具方法
    // ============================================================

    /**
     * 缓存 TTL + 双向随机偏移（防雪崩）
     *
     * <p>以 {@code baseSeconds} 为中心，± {@code jitterMaxSeconds} 范围内随机偏移。
     * 下限不低于 60s，防止缓存过早过期导致缓存穿透。
     *
     * @param baseSeconds       TTL 基础值（秒）
     * @param jitterMaxSeconds  最大偏移量（秒），范围 [0, jitterMax)
     * @return 含抖动的 TTL（秒），≥ 60s
     */
    public static long cacheTtlWithJitter(long baseSeconds, int jitterMaxSeconds) {
        int jitter = ThreadLocalRandom.current().nextInt(jitterMaxSeconds);
        boolean plus = ThreadLocalRandom.current().nextBoolean();
        return plus ? baseSeconds + jitter : Math.max(60, baseSeconds - jitter);
    }
}
