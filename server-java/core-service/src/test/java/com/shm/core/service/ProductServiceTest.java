package com.shm.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shm.common.constant.CacheConstants;
import com.shm.common.constant.RedisKeys;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.dto.ProductSearchQuery;
import com.shm.common.model.dto.product.PublishProductRequest;
import com.shm.common.model.dto.product.UpdateProductRequest;
import com.shm.common.model.entity.Product;
import com.shm.common.model.entity.User;
import com.shm.common.util.SensitiveWordFilter;
import com.shm.core.config.CreditProperties;
import com.shm.core.repository.ProductRepository;
import com.shm.core.repository.ReviewRepository;
import com.shm.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProductService 单元测试（Phase 11 11.1.5a）
 *
 * <p>覆盖商品列表/详情/发布/编辑/删除（软删除）/我发布的，Mock 依赖层。
 * <p>Redis 缓存逻辑已通过 ProductServiceRedisTest 覆盖，此处不重复。
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

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
    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepo, userRepo, reviewRepo,
                sensitiveFilter, objectMapper, creditProps, redis);
    }

    /** 仅在需要 Redis 缓存的测试中启用 valueOps mock */
    private void mockRedisValueOps() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    // ============================================================
    // list — 商品列表
    // ============================================================

    @Test
    void list_cacheMiss_shouldQueryDbAndReturnPage() {
        mockRedisValueOps();
        // 缓存未命中 — 使用 anyString() 避免 hash 不匹配
        when(valueOps.get(anyString())).thenReturn(null);

        ProductSearchQuery query = new ProductSearchQuery();
        Product p = buildProduct(1L, 10L, "iPhone 15", "active");
        when(productRepo.listWithFilters(eq(query), eq(0), eq(20))).thenReturn(List.of(p));
        when(productRepo.countWithFilters(query)).thenReturn(1L);

        User seller = User.builder().id(10L).nickname("卖家A").creditScore(85).build();
        when(userRepo.findByIds(Set.of(10L))).thenReturn(List.of(seller));

        Map<String, Object> result = productService.list(query, 1, 20);

        assertEquals(1L, result.get("total"));
        assertEquals(1, result.get("page"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
        assertEquals(1, list.size());
        assertEquals("iPhone 15", list.get(0).get("title"));

        // 缓存写入
        verify(valueOps).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void list_emptyResult_shouldCacheEmptyMarker() {
        mockRedisValueOps();
        when(valueOps.get(anyString())).thenReturn(null);

        ProductSearchQuery query = new ProductSearchQuery();
        when(productRepo.listWithFilters(eq(query), eq(0), eq(20))).thenReturn(List.of());
        when(productRepo.countWithFilters(query)).thenReturn(0L);

        Map<String, Object> result = productService.list(query, 1, 20);

        assertEquals(0L, result.get("total"));
        // 空值缓存标记
        verify(valueOps).set(anyString(), eq(RedisKeys.EMPTY_PREFIX),
                eq((long) CacheConstants.EMPTY_VALUE_TTL_SECONDS), any());
    }

    // ============================================================
    // detail — 商品详情
    // ============================================================

    @Test
    void detail_active_shouldReturnFullDetail() {
        Product p = buildProduct(1L, 10L, "MacBook Pro", "active");
        when(productRepo.findById(1L)).thenReturn(p);

        User seller = User.builder().id(10L).nickname("卖家").creditScore(90)
                .className("软工2301").dormBuilding("B栋").avatar("/av.png").build();
        when(userRepo.findById(10L)).thenReturn(seller);
        when(reviewRepo.getAvgScores(10L)).thenReturn(Map.of("total", 5L));

        Map<String, Object> result = productService.detail(1L, 5L, "user");

        @SuppressWarnings("unchecked")
        Map<String, Object> product = (Map<String, Object>) result.get("product");
        assertNotNull(product);
        assertEquals("MacBook Pro", product.get("title"));
        assertNotNull(product.get("seller"));
        assertNotNull(product.get("review_summary"));
    }

    @Test
    void detail_offShelf_asOwner_shouldReturnDetail() {
        Product p = buildProduct(2L, 10L, "下架商品", "off_shelf");
        when(productRepo.findById(2L)).thenReturn(p);
        when(userRepo.findById(10L)).thenReturn(
                User.builder().id(10L).nickname("卖家").creditScore(80).build());
        when(reviewRepo.getAvgScores(10L)).thenReturn(Map.of());

        Map<String, Object> result = productService.detail(2L, 10L, "user");

        assertNotNull(result.get("product"));
    }

    @Test
    void detail_offShelf_asAdmin_shouldReturnDetail() {
        Product p = buildProduct(3L, 10L, "管理员查看", "off_shelf");
        when(productRepo.findById(3L)).thenReturn(p);
        when(userRepo.findById(10L)).thenReturn(
                User.builder().id(10L).nickname("卖家").creditScore(80).build());
        when(reviewRepo.getAvgScores(10L)).thenReturn(Map.of());

        Map<String, Object> result = productService.detail(3L, 99L, "admin");

        assertNotNull(result.get("product"));
    }

    @Test
    void detail_offShelf_asStranger_shouldThrowNotFound() {
        Product p = buildProduct(4L, 10L, "下架", "off_shelf");
        when(productRepo.findById(4L)).thenReturn(p);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                productService.detail(4L, 50L, "user"));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void detail_notFound_shouldThrowBusinessException() {
        when(productRepo.findById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                productService.detail(999L, 1L, "user"));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ============================================================
    // create — 发布商品
    // ============================================================

    @Test
    void create_success_shouldInsertAndEvictCache() throws Exception {
        PublishProductRequest req = buildPublishRequest("全新iPhone", "电子产品");
        when(creditProps.getPublishThreshold()).thenReturn(60);
        when(sensitiveFilter.containsSensitive("全新iPhone")).thenReturn(false);
        when(sensitiveFilter.containsSensitive("九成新")).thenReturn(false);
        when(sensitiveFilter.containsSensitive("图书馆")).thenReturn(false);

        Product created = buildProduct(10L, 1L, "全新iPhone", "active");
        when(productRepo.create(any(Product.class))).thenReturn(created);

        User seller = User.builder().id(1L).nickname("发布者").build();
        when(userRepo.findById(1L)).thenReturn(seller);

        // Mock SCAN for evictProductListCache
        @SuppressWarnings("unchecked")
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(false);
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);

        Map<String, Object> result = productService.create(1L, 80, req);

        assertNotNull(result);
        assertEquals("全新iPhone", result.get("title"));
    }

    @Test
    void create_lowCredit_shouldThrowBusinessException() {
        PublishProductRequest req = buildPublishRequest("测试", "其他");
        when(creditProps.getPublishThreshold()).thenReturn(70);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                productService.create(1L, 50, req));

        assertEquals(ErrorCode.CREDIT_TOO_LOW_PUBLISH.getCode(), ex.getCode());
    }

    @Test
    void create_tooManyImages_shouldThrowBusinessException() {
        PublishProductRequest req = buildPublishRequest("测试", "其他");
        req.setImages(List.of("1.jpg", "2.jpg", "3.jpg", "4.jpg", "5.jpg", "6.jpg", "7.jpg"));
        when(creditProps.getPublishThreshold()).thenReturn(60);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                productService.create(1L, 80, req));

        assertEquals(ErrorCode.TOO_MANY_IMAGES.getCode(), ex.getCode());
    }

    @Test
    void create_priceExceedsOriginal_shouldThrowBusinessException() {
        PublishProductRequest req = buildPublishRequest("测试", "其他");
        req.setPrice(new BigDecimal("200"));
        req.setOriginalPrice(new BigDecimal("100"));
        when(creditProps.getPublishThreshold()).thenReturn(60);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                productService.create(1L, 80, req));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
    }

    @Test
    void create_sensitiveTitle_shouldThrowBusinessException() {
        PublishProductRequest req = buildPublishRequest("敏感标题", "其他");
        when(creditProps.getPublishThreshold()).thenReturn(60);
        when(sensitiveFilter.containsSensitive("敏感标题")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                productService.create(1L, 80, req));

        assertEquals(ErrorCode.SENSITIVE_WORD.getCode(), ex.getCode());
    }

    // ============================================================
    // update — 编辑商品
    // ============================================================

    @Test
    void update_success_shouldUpdateAndEvictCache() {
        Product existing = buildProduct(1L, 10L, "旧标题", "active");
        when(productRepo.findById(1L)).thenReturn(existing);

        UpdateProductRequest req = new UpdateProductRequest();
        req.setTitle("新标题");
        when(sensitiveFilter.containsSensitive("新标题")).thenReturn(false);

        @SuppressWarnings("unchecked")
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(false);
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);

        Product updated = buildProduct(1L, 10L, "新标题", "active");
        when(productRepo.findById(1L)).thenReturn(existing)
                .thenReturn(updated); // 第一次 findById 校验，第二次回显
        when(userRepo.findById(10L)).thenReturn(
                User.builder().id(10L).nickname("卖家").build());

        Map<String, Object> result = productService.update(1L, 10L, req);

        assertEquals("新标题", result.get("title"));
        verify(productRepo).update(any(Product.class));
    }

    @Test
    void update_notFound_shouldThrowBusinessException() {
        when(productRepo.findById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                productService.update(999L, 10L, new UpdateProductRequest()));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void update_notOwner_shouldThrowBusinessException() {
        Product existing = buildProduct(1L, 10L, "标题", "active");
        when(productRepo.findById(1L)).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                productService.update(1L, 99L, new UpdateProductRequest()));

        assertEquals(ErrorCode.NOT_OWNER.getCode(), ex.getCode());
    }

    @Test
    void update_soldStatus_shouldThrowBusinessException() {
        Product existing = buildProduct(1L, 10L, "已售", "sold");
        when(productRepo.findById(1L)).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                productService.update(1L, 10L, new UpdateProductRequest()));

        assertEquals(ErrorCode.PRODUCT_NOT_ACTIVE.getCode(), ex.getCode());
    }

    // ============================================================
    // delete — 软删除
    // ============================================================

    @Test
    void delete_success_shouldSoftDelete() {
        Product existing = buildProduct(1L, 10L, "商品", "active");
        when(productRepo.findByIdForUpdate(1L)).thenReturn(existing);

        @SuppressWarnings("unchecked")
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(false);
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);

        assertDoesNotThrow(() -> productService.delete(1L, 10L));
        verify(productRepo).updateStatus(1L, "deleted");
    }

    @Test
    void delete_notFound_shouldThrowBusinessException() {
        when(productRepo.findByIdForUpdate(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                productService.delete(999L, 10L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void delete_notOwner_shouldThrowBusinessException() {
        Product existing = buildProduct(1L, 10L, "商品", "active");
        when(productRepo.findByIdForUpdate(1L)).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                productService.delete(1L, 99L));

        assertEquals(ErrorCode.NOT_OWNER.getCode(), ex.getCode());
    }

    @Test
    void delete_notActive_shouldThrowBusinessException() {
        Product existing = buildProduct(1L, 10L, "商品", "sold");
        when(productRepo.findByIdForUpdate(1L)).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                productService.delete(1L, 10L));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
    }

    // ============================================================
    // findBySeller — 我发布的
    // ============================================================

    @Test
    void findBySeller_shouldReturnPaginatedProducts() {
        Product p = buildProduct(1L, 10L, "我的商品", "active");
        when(productRepo.findBySellerId(10L, null, 0, 20)).thenReturn(List.of(p));
        when(productRepo.countBySellerId(10L, null)).thenReturn(1L);

        Map<String, Object> result = productService.findBySeller(10L, null, 1, 20);

        assertEquals(1L, result.get("total"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
        assertEquals(1, list.size());
    }

    // ---- 辅助 ----

    private Product buildProduct(Long id, Long sellerId, String title, String status) {
        return Product.builder()
                .id(id).sellerId(sellerId).title(title)
                .description("描述").category("电子产品").condition("良好")
                .originalPrice(new BigDecimal("5000")).price(new BigDecimal("3000"))
                .tradeLocation("图书馆").negotiable(true)
                .images("[\"a.jpg\",\"b.jpg\"]")
                .status(status)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    private PublishProductRequest buildPublishRequest(String title, String category) {
        PublishProductRequest req = new PublishProductRequest();
        req.setTitle(title);
        req.setDescription("九成新");
        req.setCategory(category);
        req.setCondition("良好");
        req.setOriginalPrice(new BigDecimal("5000"));
        req.setPrice(new BigDecimal("3000"));
        req.setTradeLocation("图书馆");
        req.setNegotiable(true);
        req.setImages(List.of("a.jpg", "b.jpg"));
        return req;
    }
}
