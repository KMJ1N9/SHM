package com.shm.common.util;

import com.shm.common.model.page.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResponseBuilder 单元测试（Phase 11 11.1.3）
 *
 * <p>验证统一 JSON 响应格式构造器的 6 个静态方法，
 * 确保输出与 Node.js Express 后端 { code, message, data } 格式完全一致。
 */
class ResponseBuilderTest {

    // ============================================================
    // ok() — 成功响应
    // ============================================================

    @Test
    void ok_noArgs_shouldReturnCodeZeroAndMessageOk() {
        Map<String, Object> result = ResponseBuilder.ok();

        assertEquals(0, result.get("code"));
        assertEquals("ok", result.get("message"));
        assertNull(result.get("data"));
        assertEquals(3, result.size());
    }

    @Test
    void ok_withData_shouldContainPassedObject() {
        Map<String, Object> payload = Map.of("id", 1, "name", "测试");
        Map<String, Object> result = ResponseBuilder.ok(payload);

        assertEquals(0, result.get("code"));
        assertEquals("ok", result.get("message"));
        assertSame(payload, result.get("data"));
    }

    @Test
    void ok_withCustomMessage_shouldUseGivenMessage() {
        Map<String, Object> result = ResponseBuilder.ok("登录成功", Map.of("token", "abc"));

        assertEquals(0, result.get("code"));
        assertEquals("登录成功", result.get("message"));
        assertEquals("abc", ((Map<?, ?>) result.get("data")).get("token"));
    }

    @Test
    void ok_codeFieldShouldBeInteger() {
        Map<String, Object> result = ResponseBuilder.ok();

        assertInstanceOf(Integer.class, result.get("code"));
    }

    // ============================================================
    // page() — 分页成功
    // ============================================================

    @Test
    void page_shouldWrapPageResultInData() {
        List<Map<String, Object>> list = List.of(
                Map.of("id", 1, "title", "商品A"),
                Map.of("id", 2, "title", "商品B"));
        Map<String, Object> result = ResponseBuilder.page(list, 42L, 2, 20);

        assertEquals(0, result.get("code"));
        assertEquals("ok", result.get("message"));

        // data 是 PageResult
        Object data = result.get("data");
        assertInstanceOf(PageResult.class, data);
        @SuppressWarnings("unchecked")
        PageResult<Map<String, Object>> pageResult = (PageResult<Map<String, Object>>) data;
        assertEquals(2, pageResult.getList().size());
        assertEquals(42L, pageResult.getTotal());
        assertEquals(2, pageResult.getPage());
        assertEquals(20, pageResult.getPageSize());
    }

    @Test
    void page_emptyList_shouldReturnZeroTotal() {
        List<Object> empty = List.of();
        Map<String, Object> result = ResponseBuilder.page(empty, 0L, 1, 10);

        @SuppressWarnings("unchecked")
        PageResult<Object> pageResult = (PageResult<Object>) result.get("data");
        assertEquals(0L, pageResult.getTotal());
        assertTrue(pageResult.getList().isEmpty());
        assertEquals(1, pageResult.getPage());
    }

    @Test
    void page_pageInfoShouldMatchInput() {
        List<String> list = List.of("item1", "item2", "item3");
        Map<String, Object> result = ResponseBuilder.page(list, 100L, 3, 30);

        @SuppressWarnings("unchecked")
        PageResult<String> pageResult = (PageResult<String>) result.get("data");
        assertEquals(3, pageResult.getPage());
        assertEquals(30, pageResult.getPageSize());
        assertEquals(3, pageResult.getList().size());
        assertEquals(100L, pageResult.getTotal());
    }

    // ============================================================
    // error() — 错误响应
    // ============================================================

    @Test
    void error_basic_shouldReturnErrorCodeAndNullData() {
        Map<String, Object> result = ResponseBuilder.error(1001, "未登录或登录已过期");

        assertEquals(1001, result.get("code"));
        assertEquals("未登录或登录已过期", result.get("message"));
        assertNull(result.get("data"));
    }

    @Test
    void error_withDetail_shouldContainDetailField() {
        Map<String, Object> result = ResponseBuilder.error(6001, "服务内部错误", "java.lang.NPE at ...");

        assertEquals(6001, result.get("code"));
        assertEquals("服务内部错误", result.get("message"));
        assertNull(result.get("data"));
        assertEquals("java.lang.NPE at ...", result.get("detail"));
    }

    @Test
    void error_nullDetail_shouldNotAddDetailKey() {
        Map<String, Object> result = ResponseBuilder.error(4001, "参数校验失败", null);

        assertFalse(result.containsKey("detail"));
    }

    @Test
    void error_codeFieldShouldBeInteger() {
        Map<String, Object> result = ResponseBuilder.error(6999, "服务暂不可用");

        assertInstanceOf(Integer.class, result.get("code"));
        assertEquals(6999, result.get("code"));
    }
}
