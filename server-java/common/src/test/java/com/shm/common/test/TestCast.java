package com.shm.common.test;

/**
 * 测试工具：类型安全转换助手。
 *
 * <p>Java 泛型擦除导致从 {@code Map<String, Object>} 取值时产生
 * unchecked cast 警告。测试代码中 Service 层返回 {@code Map<String, Object>}，
 * 断言时需要提取嵌套的 List/Map。本工具将 {@code @SuppressWarnings("unchecked")}
 * 集中到一处，避免散落在数十个测试文件中。
 *
 * <p>使用示例：
 * <pre>{@code
 * Map<String, Object> result = service.list(1, 20);
 * List<Map<String, Object>> items = TestCast.cast(result.get("list"));
 * Map<String, Object> data = TestCast.cast(response.get("data"));
 * }</pre>
 *
 * <p>等价于手动写：
 * <pre>{@code
 * @SuppressWarnings("unchecked")
 * List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("list");
 * }</pre>
 *
 * @author Claude Code
 * @since 2026-06-14
 */
public final class TestCast {

    private TestCast() {
        // 工具类禁止实例化
    }

    /**
     * 安全的 unchecked cast，将警告集中在此处。
     *
     * @param obj 待转换的对象（通常来自 {@code Map.get()} 返回值）
     * @param <T> 目标类型（由调用处类型推断自动确定）
     * @return 转换后的对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T cast(Object obj) {
        return (T) obj;
    }
}
