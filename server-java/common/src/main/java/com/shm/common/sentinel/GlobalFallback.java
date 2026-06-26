package com.shm.common.sentinel;

import java.util.Map;

/**
 * 全局 Sentinel Fallback（Phase 17）
 *
 * <p>所有 @SentinelResource 被熔断/降级后的统一 Fallback 处理。
 * 方法为 static，供 {@code fallbackClass} 引用。
 *
 * <h3>错误码约定</h3>
 * <ul>
 *   <li>6004 — 服务降级（与 Node.js 错误码体系一致，该编码为预留值）</li>
 * </ul>
 */
public final class GlobalFallback {

    private GlobalFallback() {
        /* 工具类禁止实例化 */
    }

    /**
     * 通用降级 Fallback
     *
     * @param t 业务异常（Sentinel 熔断时传入 DegradeException，运行时异常传入原始异常）
     * @return 统一降级响应
     */
    public static Map<String, Object> handleFallback(Throwable t) {
        return Map.of("code", 6004, "message", "服务暂时不可用，请稍后再试");
    }
}
