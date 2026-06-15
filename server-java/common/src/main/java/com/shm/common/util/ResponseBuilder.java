package com.shm.common.util;

import com.shm.common.model.page.PageResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一 JSON 响应构造器
 * <p>
 * 格式：{ code: 0, message: "ok", data: {...} }
 * 与 Node.js Express 后端完全一致
 */
public final class ResponseBuilder {

    private ResponseBuilder() {
        // 工具类，禁止实例化
    }

    /** 成功（无数据） */
    public static Map<String, Object> ok() {
        return build(0, "ok", null);
    }

    /** 成功（带数据） */
    public static Map<String, Object> ok(Object data) {
        return build(0, "ok", data);
    }

    /** 成功（自定义消息） */
    public static Map<String, Object> ok(String message, Object data) {
        return build(0, message, data);
    }

    /** 分页成功 */
    public static Map<String, Object> page(List<?> list, long total, int page, int pageSize) {
        return ok(PageResult.of(list, total, page, pageSize));
    }

    /** 错误（与 Node.js 一致，始终包含 detail 字段） */
    public static Map<String, Object> error(int code, String message) {
        Map<String, Object> result = build(code, message, null);
        result.put("detail", null);
        return result;
    }

    /** 错误（带详情，仅在 development 环境暴露） */
    public static Map<String, Object> error(int code, String message, String detail) {
        Map<String, Object> result = build(code, message, null);
        if (detail != null) {
            result.put("detail", detail);
        }
        return result;
    }

    private static Map<String, Object> build(int code, String message, Object data) {
        Map<String, Object> result = new HashMap<>(3);
        result.put("code", code);
        result.put("message", message);
        result.put("data", data);
        return result;
    }
}
