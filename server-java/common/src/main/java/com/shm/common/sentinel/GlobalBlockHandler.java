package com.shm.common.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;

import java.util.Map;

/**
 * 全局 Sentinel BlockHandler（Phase 17）
 *
 * <p>所有限流/降级/热点参数被 Block 后的统一响应处理。
 * 方法为 static，供 {@code @SentinelResource} 的 {@code blockHandlerClass} 引用。
 *
 * <h3>错误码约定</h3>
 * <ul>
 *   <li>4006 — 限流（与 Node.js rate-limiter.js 一致）</li>
 * </ul>
 */
public final class GlobalBlockHandler {

    private GlobalBlockHandler() {
        /* 工具类禁止实例化 */
    }

    /**
     * 通用限流 BlockHandler
     *
     * @param e Sentinel BlockException
     * @return 统一限流响应
     */
    public static Map<String, Object> handleBlock(BlockException e) {
        return Map.of("code", 4006, "message", "请求过于频繁，请稍后再试");
    }

    /**
     * 商品详情热点参数 BlockHandler
     *
     * <p>签名必须与 Controller 方法一致（参数列表 + BlockException），
     * Sentinel 通过反射匹配。
     *
     * @param productId 被限流的商品 ID
     * @param e         Sentinel BlockException
     * @return 统一限流响应
     */
    public static Map<String, Object> productDetailBlock(Long productId, BlockException e) {
        return Map.of("code", 4006, "message", "该商品访问过热，请稍后再试");
    }
}
