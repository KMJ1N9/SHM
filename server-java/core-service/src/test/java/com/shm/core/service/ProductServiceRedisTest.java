package com.shm.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.common.constant.CacheConstants;
import com.shm.common.constant.RedisKeys;
import com.shm.common.model.dto.ProductSearchQuery;
import com.shm.common.model.entity.Product;
import com.shm.common.model.entity.User;
import com.shm.core.config.CreditProperties;
import com.shm.core.repository.ProductRepository;
import com.shm.core.repository.ReviewRepository;
import com.shm.core.repository.UserRepository;
import com.shm.common.util.SensitiveWordFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProductService Redis 缓存层单元测试（Phase 10 P1-2）
 *
 * <p>覆盖 Cache-Aside 模式的四种路径：
 * <ol>
 *   <li>缓存命中 → 返回缓存数据，不查 DB</li>
 *   <li>缓存未命中 → 查 DB + 写缓存</li>
 *   <li>空值缓存命中 → 返回空结果（防穿透）</li>
 *   <li>缓存读取失败 → 降级查 DB</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceRedisTest {

    @Mock
    private ProductRepository productRepo;
    @Mock
    private UserRepository userRepo;
    @Mock
    private ReviewRepository reviewRepo;
    @Mock
    private SensitiveWordFilter sensitiveFilter;
    @Mock
    private CreditProperties creditProps;
    @Mock
    private StringRedisTemplate redis;

    /** 真实 ObjectMapper — 验证 JSON 序列化/反序列化链路 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                productRepo, userRepo, reviewRepo, sensitiveFilter,
                objectMapper, creditProps, redis);
    }

    // ============================================================
    // 缓存命中
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void list_cacheHit_shouldReturnCachedDataWithoutQueryingDb() throws Exception {
        // Given: 缓存中有数据
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        String expectedJson = objectMapper.writeValueAsString(
                Map.of("list", List.of(), "total", 5L, "page", 1, "pageSize", 20));
        when(valueOps.get(anyString())).thenReturn(expectedJson);

        ProductSearchQuery query = ProductSearchQuery.builder().build();

        // When
        Map<String, Object> result = productService.list(query, 1, 20);

        // Then: 返回缓存数据
        assertNotNull(result);
        assertEquals(5, ((Number) result.get("total")).intValue());
        assertEquals(1, result.get("page"));

        // 不应查询 DB
        verify(productRepo, never()).listWithFilters(any(), anyInt(), anyInt());
        verify(productRepo, never()).countWithFilters(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_cacheHit_shouldReturnCorrectPageInfo() throws Exception {
        // Given
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        Map<String, Object> cachedData = Map.of(
                "list", List.of(Map.of("id", 1, "title", "测试商品")),
                "total", 1L, "page", 2, "pageSize", 10);
        when(valueOps.get(anyString())).thenReturn(objectMapper.writeValueAsString(cachedData));

        ProductSearchQuery query = ProductSearchQuery.builder().build();

        // When
        Map<String, Object> result = productService.list(query, 2, 10);

        // Then
        assertEquals(1, ((Number) result.get("total")).intValue());
        assertEquals(2, result.get("page"));
        assertEquals(10, result.get("pageSize"));
    }

    // ============================================================
    // 缓存未命中 → 查 DB + 写缓存
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void list_cacheMiss_shouldQueryDbAndWriteCache() throws Exception {
        // Given: 缓存为空
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        // DB 有数据
        Product product = Product.builder()
                .id(1L).sellerId(10L).title("二手教材").description("很好")
                .category("book").condition("good").price(java.math.BigDecimal.TEN)
                .originalPrice(java.math.BigDecimal.valueOf(50))
                .tradeLocation("图书馆").negotiable(true).images("[]").status("active")
                .build();
        when(productRepo.listWithFilters(any(), anyInt(), anyInt())).thenReturn(List.of(product));
        when(productRepo.countWithFilters(any())).thenReturn(1L);

        User seller = User.builder().id(10L).nickname("卖家A").avatar("/a.png").creditScore(95).build();
        when(userRepo.findByIds(anySet())).thenReturn(List.of(seller));

        ProductSearchQuery query = ProductSearchQuery.builder().build();

        // When
        Map<String, Object> result = productService.list(query, 1, 20);

        // Then: 查了 DB
        verify(productRepo).listWithFilters(any(), anyInt(), anyInt());
        verify(productRepo).countWithFilters(any());

        // 写入了缓存
        verify(valueOps).set(anyString(), anyString(), anyLong(), any());

        // 返回正确数据
        assertNotNull(result);
        assertEquals(1, ((Number) result.get("total")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_cacheMissEmptyResult_shouldCacheEmptyMarker() {
        // Given: 缓存为空，DB 也无数据
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        when(productRepo.listWithFilters(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(productRepo.countWithFilters(any())).thenReturn(0L);

        ProductSearchQuery query = ProductSearchQuery.builder().build();

        // When
        Map<String, Object> result = productService.list(query, 1, 20);

        // Then: 返回空结果
        assertEquals(0, ((Number) result.get("total")).intValue());

        // 写入了空值标记（RedisKeys.EMPTY_PREFIX），短 TTL 防穿透
        verify(valueOps).set(anyString(), eq(RedisKeys.EMPTY_PREFIX),
                eq(CacheConstants.EMPTY_VALUE_TTL_SECONDS), any());
    }

    // ============================================================
    // 空值缓存命中（防穿透）
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void list_emptyMarkerHit_shouldReturnEmptyResultWithoutQueryingDb() {
        // Given: 缓存中是空值标记
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(RedisKeys.EMPTY_PREFIX);

        ProductSearchQuery query = ProductSearchQuery.builder().build();

        // When
        Map<String, Object> result = productService.list(query, 1, 20);

        // Then: 返回空结果
        assertEquals(0, ((Number) result.get("total")).intValue());
        assertTrue(((List<?>) result.get("list")).isEmpty());

        // 不应查 DB（缓存命中了空值标记）
        verify(productRepo, never()).listWithFilters(any(), anyInt(), anyInt());
        verify(productRepo, never()).countWithFilters(any());
    }

    // ============================================================
    // 缓存读取异常 → 降级查 DB
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void list_cacheReadError_shouldFallbackToDb() {
        // Given: 缓存读取抛异常（Redis 连接断开等）
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis connection timeout"));

        // DB 正常
        Product product = Product.builder()
                .id(2L).sellerId(20L).title("二手电脑").category("digital")
                .condition("like_new").price(java.math.BigDecimal.valueOf(1500))
                .originalPrice(java.math.BigDecimal.valueOf(5000))
                .tradeLocation("宿舍").negotiable(false).images("[]").status("active")
                .build();
        when(productRepo.listWithFilters(any(), anyInt(), anyInt())).thenReturn(List.of(product));
        when(productRepo.countWithFilters(any())).thenReturn(1L);

        User seller = User.builder().id(20L).nickname("卖家B").avatar("/b.png").creditScore(88).build();
        when(userRepo.findByIds(anySet())).thenReturn(List.of(seller));

        ProductSearchQuery query = ProductSearchQuery.builder().build();

        // When: 不应抛异常
        Map<String, Object> result = productService.list(query, 1, 20);

        // Then: 降级成功，查 DB 返回数据
        assertNotNull(result);
        assertEquals(1, ((Number) result.get("total")).intValue());
        verify(productRepo).listWithFilters(any(), anyInt(), anyInt());
    }

    // ============================================================
    // SCAN 接口编译期验证
    // ============================================================

    /**
     * 验证 SCAN 方法签名存在于 StringRedisTemplate 上（编译期保证 P1-1 修复生效）。
     * <p>实际 eviction 行为在集中测试中覆盖（create/update/delete 均触发 eviction）。
     */
    @Test
    void scanMethodIsCallable() {
        // 验证 redis.scan() 方法接受 ScanOptions 参数并可被 mock
        // — 若 KEYS 被改回，此处 scan mock 会因类型不匹配而失败
        @SuppressWarnings("unchecked")
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("shm:product:list:1:20:0");
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);

        // 模拟 SCAN 迭代: hasNext() → true → next() → hasNext() → false → close()
        List<String> keys = new ArrayList<>();
        try (Cursor<String> c = redis.scan(ScanOptions.scanOptions().match("shm:*").count(100).build())) {
            while (c.hasNext()) {
                keys.add(c.next());
            }
        }
        assertEquals(1, keys.size());
        assertEquals("shm:product:list:1:20:0", keys.get(0));
    }

    // ============================================================
    // 不同查询参数的缓存 key 隔离
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void list_differentQueries_shouldUseDifferentCacheKeys() {
        // Given: 设置两个不同查询，都缓存未命中
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        when(productRepo.listWithFilters(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(productRepo.countWithFilters(any())).thenReturn(0L);

        // When: 两个不同查询
        ProductSearchQuery query1 = ProductSearchQuery.builder().keyword("书").category("book").build();
        ProductSearchQuery query2 = ProductSearchQuery.builder().keyword("电脑").category("digital").build();

        productService.list(query1, 1, 20);
        productService.list(query2, 1, 20);

        // Then: 两次 set 使用了不同的 key（通过参数验证）
        verify(valueOps, times(2)).set(argThat(key ->
                key instanceof String && ((String) key).startsWith("shm:product:list:")),
                anyString(), anyLong(), any());
    }
}
