package com.shm.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.common.model.entity.User;
import com.shm.core.feign.ImConnectorFeign;
import com.shm.core.mapper.UserMapper;
import com.shm.core.security.UserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Core Service 端到端集成测试（Phase 11.2.7）
 *
 * <p>使用 @SpringBootTest 加载完整 Spring 上下文，验证 Controller → Service →
 * Repository → MySQL 全链路正确性。仅 Mock 外部依赖（IM Connector Feign / Redis）。
 *
 * <p>安全策略：{@code addFilters = false} 禁用 Spring Security Filter Chain，
 * 通过 {@link SecurityContextHolder} 编程式注入测试用户身份。
 *
 * <p>事务策略：每个 @Test 方法通过 @Transactional 自动回滚。
 *
 * <h3>覆盖场景</h3>
 * <ul>
 *   <li>E2E-01：健康检查（无需认证）</li>
 *   <li>E2E-02：商品生命周期 — 发布 → 列表 → 详情 → 编辑 → 删除</li>
 *   <li>E2E-03：订单生命周期 — 创建 → 幂等 → 面交 → 确认 → 取消</li>
 *   <li>E2E-04：边界条件 — 角色列表 / 不存在商品</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2026-06-14
 */
@SpringBootTest(classes = TestCoreApplication.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CoreServiceE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private ImConnectorFeign imConnectorFeign;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    /** 测试买家（每个 @Transactional 测试中创建，自动回滚） */
    private User testBuyer;

    /** 测试卖家 */
    private User testSeller;

    /** 买家 Principal */
    private UserPrincipal buyerPrincipal;

    /** 卖家 Principal */
    private UserPrincipal sellerPrincipal;

    @BeforeEach
    void setUp() {
        // 创建测试用户（在 @Transactional 事务中，测试结束自动回滚）
        testBuyer = new User();
        testBuyer.setPhone("13820000101");
        testBuyer.setNickname("E2E测试买家");
        testBuyer.setAvatar("");
        testBuyer.setClassName("CS2024");
        testBuyer.setDormBuilding("北区");
        testBuyer.setRole("user");
        testBuyer.setCreditScore(100);
        testBuyer.setStatus("active");
        testBuyer.setTokenVersion(0);
        userMapper.insert(testBuyer);

        testSeller = new User();
        testSeller.setPhone("13820000102");
        testSeller.setNickname("E2E测试卖家");
        testSeller.setAvatar("");
        testSeller.setClassName("CS2024");
        testSeller.setDormBuilding("南区");
        testSeller.setRole("user");
        testSeller.setCreditScore(100);
        testSeller.setStatus("active");
        testSeller.setTokenVersion(0);
        userMapper.insert(testSeller);

        buyerPrincipal = new UserPrincipal(testBuyer);
        sellerPrincipal = new UserPrincipal(testSeller);

        // Mock Redis：允许获取分布式锁，缓存全部未命中
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = org.mockito.Mockito.mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);

        // Mock IM：消息推送成功
        when(imConnectorFeign.sendSystemMessage(anyString(), anyString(), anyString(), any()))
                .thenReturn(Map.of("code", 0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ============================================================
    // E2E-01: 健康检查（无需认证）
    // ============================================================

    @Test
    @org.junit.jupiter.api.Order(1)
    @Transactional
    @DisplayName("E2E-01: GET /api/health — 健康检查")
    void healthCheck_shouldReturnHealthy() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.status").value("healthy"));
    }

    // ============================================================
    // E2E-02: 商品生命周期
    // ============================================================

    @Test
    @org.junit.jupiter.api.Order(2)
    @Transactional
    @DisplayName("E2E-02a: POST /api/products — 发布商品")
    void productLifecycle_create() throws Exception {
        loginAs(sellerPrincipal);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "title", "E2E-测试商品-Java编程思想",
                "description", "正版书籍，几乎全新",
                "category", "书籍",
                "condition", "95新",
                "original_price", new BigDecimal("79.00"),
                "price", new BigDecimal("35.00"),
                "trade_location", "图书馆",
                "negotiable", true,
                "images", List.of("https://example.com/img1.jpg")
        ));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.title").value("E2E-测试商品-Java编程思想"))
                .andExpect(jsonPath("$.data.price").value("35.00"))
                .andExpect(jsonPath("$.data.status").value("active"));
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @Transactional
    @DisplayName("E2E-02b: GET /api/products — 商品列表")
    void productLifecycle_list() throws Exception {
        loginAs(sellerPrincipal);
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "E2E-列表测试商品", "description", "列表测试",
                "category", "数码", "condition", "全新",
                "original_price", new BigDecimal("200.00"), "price", new BigDecimal("100.00"),
                "trade_location", "图书馆", "negotiable", false, "images", List.of("https://example.com/img.jpg")
        ));
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful());

        SecurityContextHolder.clearContext();
        // 使用 category 精确过滤（避免 FULLTEXT MATCH AGAINST 中文分词与 innodb_ft_min_token_size 交互问题）
        mockMvc.perform(get("/api/products")
                        .param("page", "1").param("pageSize", "20")
                        .param("category", "数码"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list.length()").isNumber())
                .andExpect(jsonPath("$.data.list[0].title").exists())
                .andExpect(jsonPath("$.data.list[0].price").exists())
                .andExpect(jsonPath("$.data.list[0].negotiable").exists());
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @Transactional
    @DisplayName("E2E-02c: GET /api/products/:id — 商品详情")
    void productLifecycle_detail() throws Exception {
        Long productId = createProduct(sellerPrincipal, "E2E-详情测试商品",
                "详情描述", "生活用品", "8成新", new BigDecimal("50.00"), new BigDecimal("20.00"));

        // detail() 返回商品对象直接作为 data（与 Node.js 一致）
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.title").value("E2E-详情测试商品"))
                .andExpect(jsonPath("$.data.price").value("20.00"))
                .andExpect(jsonPath("$.data.original_price").value("50.00"))
                .andExpect(jsonPath("$.data.negotiable").value(1));
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @Transactional
    @DisplayName("E2E-02d: PUT /api/products/:id — 编辑商品")
    void productLifecycle_update() throws Exception {
        Long productId = createProduct(sellerPrincipal, "E2E-原始标题",
                "原始描述", "数码", "9成新", new BigDecimal("300.00"), new BigDecimal("150.00"));

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "title", "E2E-更新后的标题",
                "price", new BigDecimal("120.00")
        ));
        mockMvc.perform(put("/api/products/" + productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("E2E-更新后的标题"))
                .andExpect(jsonPath("$.data.price").value("120.00"));
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @Transactional
    @DisplayName("E2E-02e: DELETE /api/products/:id — 删除商品")
    void productLifecycle_delete() throws Exception {
        Long productId = createProduct(sellerPrincipal, "E2E-待删除商品",
                "即将删除", "书籍", "全新", new BigDecimal("30.00"), new BigDecimal("15.00"));

        mockMvc.perform(delete("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(nullValue()));

        // 验证已删除（status="deleted" → detail 返回 NOT_FOUND = HTTP 404 + code 2001）
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2001));
    }

    // ============================================================
    // E2E-03: 订单生命周期
    // ============================================================

    @Test
    @org.junit.jupiter.api.Order(7)
    @Transactional
    @DisplayName("E2E-03a: POST /api/orders — 创建订单")
    void orderLifecycle_create() throws Exception {
        Long productId = createProduct(sellerPrincipal, "E2E-订单测试商品",
                "订单流程测试", "数码", "95新", new BigDecimal("500.00"), new BigDecimal("200.00"));

        loginAs(buyerPrincipal);
        // idempotentKey 由服务端生成 (buyerId + "_" + productId)，客户端不传
        String orderBody = objectMapper.writeValueAsString(Map.of(
                "product_id", productId
        ));
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.product_id").value(productId))
                .andExpect(jsonPath("$.data.status").value("pending"));
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @Transactional
    @DisplayName("E2E-03b: 订单幂等性")
    void orderLifecycle_idempotent() throws Exception {
        Long productId = createProduct(sellerPrincipal, "E2E-幂等测试商品",
                "幂等测试", "书籍", "全新", new BigDecimal("60.00"), new BigDecimal("30.00"));

        // 幂等键由服务端生成: buyerId + "_" + productId，同一买家对同一商品重复下单自动幂等
        loginAs(buyerPrincipal);
        String orderBody = objectMapper.writeValueAsString(Map.of(
                "product_id", productId
        ));

        // 第一次下单
        String firstJson = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        Long firstOrderId = extractId(objectMapper.readValue(firstJson, Map.class), "data");

        // 第二次下单（相同 buyer + product → 相同幂等键）— 应返回同一订单
        String secondJson = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        Long secondOrderId = extractId(objectMapper.readValue(secondJson, Map.class), "data");

        assertEquals(firstOrderId, secondOrderId, "相同买家对同一商品重复下单，幂等键相同，应返回同一订单");
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @Transactional
    @DisplayName("E2E-03c: PUT /api/orders/:id/mark-as-met — 卖家标记面交")
    void orderLifecycle_markAsMet() throws Exception {
        Long orderId = prepareOrder();

        loginAs(sellerPrincipal);
        mockMvc.perform(put("/api/orders/" + orderId + "/met"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("met"));
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @Transactional
    @DisplayName("E2E-03d: PUT /api/orders/:id/confirm — 买家确认收货")
    void orderLifecycle_confirm() throws Exception {
        Long orderId = prepareOrder();

        // 卖家标记面交
        loginAs(sellerPrincipal);
        mockMvc.perform(put("/api/orders/" + orderId + "/met"));

        // 买家确认收货
        loginAs(buyerPrincipal);
        mockMvc.perform(put("/api/orders/" + orderId + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("completed"));
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @Transactional
    @DisplayName("E2E-03e: PUT /api/orders/:id/cancel — 买家取消订单")
    void orderLifecycle_cancel() throws Exception {
        Long orderId = prepareOrder();

        loginAs(buyerPrincipal);
        mockMvc.perform(put("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("cancelled"));
    }

    // ============================================================
    // E2E-04: 边界条件
    // ============================================================

    @Test
    @org.junit.jupiter.api.Order(12)
    @Transactional
    @DisplayName("E2E-04a: 订单列表按角色过滤")
    void orderList_byRole() throws Exception {
        Long orderId = prepareOrder();

        loginAs(buyerPrincipal);
        mockMvc.perform(get("/api/orders")
                        .param("role", "buyer").param("page", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list.length()").value(1));

        loginAs(sellerPrincipal);
        mockMvc.perform(get("/api/orders")
                        .param("role", "seller").param("page", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list.length()").value(1));
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @Transactional
    @DisplayName("E2E-04b: 获取不存在的商品详情应返回 NOT_FOUND")
    void productDetail_notFound_shouldReturnError() throws Exception {
        loginAs(buyerPrincipal);
        // BusinessException(NOT_FOUND) → GlobalExceptionHandler 映射为 HTTP 404
        mockMvc.perform(get("/api/products/99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2001));
    }

    // ============================================================
    // Helpers
    // ============================================================

    private void loginAs(UserPrincipal user) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** 创建商品并返回商品 ID */
    private Long createProduct(UserPrincipal seller, String title, String desc,
                                String category, String condition,
                                BigDecimal originalPrice, BigDecimal price) throws Exception {
        loginAs(seller);
        String body = objectMapper.writeValueAsString(Map.of(
                "title", title,
                "description", desc,
                "category", category,
                "condition", condition,
                "original_price", originalPrice,
                "price", price,
                "trade_location", "图书馆",
                "negotiable", true,
                "images", List.of("https://example.com/img.jpg")
        ));
        String json = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return extractId(objectMapper.readValue(json, Map.class), "data");
    }

    /** 卖家发布商品 + 买家下单，返回订单 ID */
    private Long prepareOrder() throws Exception {
        Long productId = createProduct(sellerPrincipal,
                "E2E-流程测试-" + System.nanoTime(),
                "流程测试", "数码", "95新",
                new BigDecimal("400.00"), new BigDecimal("180.00"));

        loginAs(buyerPrincipal);
        // 幂等键由服务端自动生成（buyerId + "_" + productId），客户端只传 productId
        String orderBody = objectMapper.writeValueAsString(Map.of(
                "product_id", productId
        ));
        String json = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return extractId(objectMapper.readValue(json, Map.class), "data");
    }

    /** 从 Map 中提取嵌套的 id 字段 */
    @SuppressWarnings("unchecked")
    private static Long extractId(Map<String, Object> map, String key) {
        Map<String, Object> nested = (Map<String, Object>) map.get(key);
        return ((Number) nested.get("id")).longValue();
    }
}
