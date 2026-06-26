package com.shm.common.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GlobalBlockHandler / GlobalFallback 单元测试（Phase 17）
 *
 * <p>验证所有 BlockHandler / Fallback 静态方法返回正确的错误码。
 */
class GlobalBlockHandlerTest {

    private final BlockException mockBlock = new FlowException("test-flow");

    // ============================================================
    // GlobalBlockHandler
    // ============================================================

    @Test
    void handleBlock_shouldReturnCode4006() {
        Map<String, Object> result = GlobalBlockHandler.handleBlock(mockBlock);

        assertEquals(4006, result.get("code"));
        assertEquals("请求过于频繁，请稍后再试", result.get("message"));
    }

    @Test
    void productDetailBlock_shouldReturnCode4006() {
        Map<String, Object> result = GlobalBlockHandler.productDetailBlock(1L, mockBlock);

        assertEquals(4006, result.get("code"));
        assertEquals("该商品访问过热，请稍后再试", result.get("message"));
    }

    // ============================================================
    // GlobalFallback
    // ============================================================

    @Test
    void handleFallback_shouldReturnCode6004() {
        Map<String, Object> result = GlobalFallback.handleFallback(new RuntimeException("test"));

        assertEquals(6004, result.get("code"));
        assertEquals("服务暂时不可用，请稍后再试", result.get("message"));
    }
}
