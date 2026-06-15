package com.shm.core.controller;

import com.shm.common.model.dto.ProductSearchQuery;
import com.shm.common.model.dto.product.PublishProductRequest;
import com.shm.common.model.dto.product.UpdateProductRequest;
import com.shm.core.security.UserPrincipal;
import com.shm.core.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProductController 单元测试（Phase 11.2.4）
 *
 * <p>直接实例化 Controller + Mock Service，避免 @WebMvcTest 触发 Spring Cloud
 * (Nacos/Feign/Sentinel) 自动配置。验证控制器层的参数绑定、服务委托、响应格式。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductController")
class ProductControllerTest {

    @Mock
    private ProductService productService;

    private ProductController controller;

    private UserPrincipal mockUser;

    @BeforeEach
    void setUp() {
        controller = new ProductController(productService);
        // 构造模拟用户
        com.shm.common.model.entity.User user = new com.shm.common.model.entity.User();
        user.setId(1L);
        user.setPhone("13800000001");
        user.setNickname("测试用户");
        user.setAvatar("");
        user.setClassName("CS2024");
        user.setDormBuilding("北区");
        user.setRole("user");
        user.setStatus("active");
        user.setCreditScore(100);
        mockUser = new UserPrincipal(user);
    }

    // ---- list ----

    @Test
    @DisplayName("GET /api/products — 商品列表，返回 code=0 + 分页数据")
    void list_shouldReturnPagedResult() {
        Map<String, Object> serviceResult = new LinkedHashMap<>();
        serviceResult.put("list", List.of());
        serviceResult.put("total", 0L);
        serviceResult.put("page", 1);
        serviceResult.put("pageSize", 20);
        when(productService.list(any(ProductSearchQuery.class), eq(1), eq(20)))
                .thenReturn(serviceResult);

        Map<String, Object> response = controller.list(1, 20, null, null, null, null, null, null);

        assertEquals(0, response.get("code"));
        assertEquals("ok", response.get("message"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertNotNull(data);
        assertEquals(0L, data.get("total"));
    }

    @Test
    @DisplayName("GET /api/products — pageSize 超过 50 自动截断")
    void list_shouldCapPageSizeAt50() {
        when(productService.list(any(ProductSearchQuery.class), eq(1), eq(50)))
                .thenReturn(Map.of("list", List.of(), "total", 0L));

        controller.list(1, 100, null, null, null, null, null, null);

        verify(productService).list(any(ProductSearchQuery.class), eq(1), eq(50));
    }

    @Test
    @DisplayName("GET /api/products — 关键词/分类/成色/价格/排序 正确传递")
    void list_shouldPassFiltersCorrectly() {
        when(productService.list(any(ProductSearchQuery.class), eq(1), eq(20)))
                .thenReturn(Map.of("list", List.of(), "total", 0L));

        controller.list(1, 20, "数学", "书籍", "全新",
                new BigDecimal("10.00"), new BigDecimal("100.00"), "price_asc");

        verify(productService).list(argThat(query ->
                "数学".equals(query.getKeyword()) &&
                "书籍".equals(query.getCategory()) &&
                "全新".equals(query.getCondition()) &&
                new BigDecimal("10.00").compareTo(query.getPriceMin()) == 0 &&
                new BigDecimal("100.00").compareTo(query.getPriceMax()) == 0 &&
                "price_asc".equals(query.getSort())
        ), eq(1), eq(20));
    }

    // ---- my ----

    @Test
    @DisplayName("GET /api/products/my — 我的商品列表")
    void my_shouldReturnSellerProducts() {
        Map<String, Object> serviceResult = new LinkedHashMap<>();
        serviceResult.put("list", List.of());
        serviceResult.put("total", 0L);
        when(productService.findBySeller(eq(1L), eq("active"), eq(1), eq(20)))
                .thenReturn(serviceResult);

        Map<String, Object> response = controller.my(mockUser, 1, 20, "active");

        assertEquals(0, response.get("code"));
        assertNotNull(response.get("data"));
    }

    // ---- detail ----

    @Test
    @DisplayName("GET /api/products/:id — 商品详情")
    void detail_shouldReturnProductDetail() {
        Map<String, Object> productDetail = new LinkedHashMap<>();
        productDetail.put("id", 100L);
        productDetail.put("title", "测试商品");
        productDetail.put("price", "50.00");
        when(productService.detail(eq(100L), eq(1L), eq("user")))
                .thenReturn(productDetail);

        Map<String, Object> response = controller.detail(100L, mockUser);

        assertEquals(0, response.get("code"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertEquals(100L, data.get("id"));
        assertEquals("测试商品", data.get("title"));
        assertEquals("50.00", data.get("price"));
    }

    // ---- create ----

    @Test
    @DisplayName("POST /api/products — 发布商品")
    void create_shouldReturnCreatedProduct() {
        PublishProductRequest request = PublishProductRequest.builder()
                .title("新商品")
                .description("描述")
                .category("数码")
                .condition("全新")
                .originalPrice(new BigDecimal("100.00"))
                .price(new BigDecimal("50.00"))
                .tradeLocation("图书馆")
                .negotiable(true)
                .images(List.of("https://example.com/img.jpg"))
                .build();

        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", 200L);
        created.put("title", "新商品");
        when(productService.create(eq(1L), eq(100), eq(request)))
                .thenReturn(created);

        Map<String, Object> response = controller.create(mockUser, request);

        assertEquals(0, response.get("code"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertEquals(200L, data.get("id"));
    }

    // ---- update ----

    @Test
    @DisplayName("PUT /api/products/:id — 编辑商品")
    void update_shouldReturnUpdatedProduct() {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setTitle("更新后的标题");

        Map<String, Object> updated = new LinkedHashMap<>();
        updated.put("id", 300L);
        updated.put("title", "更新后的标题");
        when(productService.update(eq(300L), eq(1L), eq(request)))
                .thenReturn(updated);

        Map<String, Object> response = controller.update(300L, mockUser, request);

        assertEquals(0, response.get("code"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertEquals("更新后的标题", data.get("title"));
    }

    // ---- delete ----

    @Test
    @DisplayName("DELETE /api/products/:id — 删除商品")
    void delete_shouldCallServiceDelete() {
        doNothing().when(productService).delete(400L, 1L);

        Map<String, Object> response = controller.delete(400L, mockUser);

        assertEquals(0, response.get("code"));
        assertNull(response.get("data"));
        verify(productService).delete(400L, 1L);
    }

    @Test
    @DisplayName("DELETE /api/products/:id — 删除他人商品时服务应抛异常，由全局异常处理器捕获")
    void delete_shouldDelegateToService_errorsHandledByGlobalHandler() {
        doThrow(new RuntimeException("无权操作")).when(productService).delete(500L, 1L);

        assertThrows(RuntimeException.class,
                () -> controller.delete(500L, mockUser));
    }

    // ---- 边界条件 ----

    @Test
    @DisplayName("list — 无数据时返回空列表")
    void list_shouldReturnEmptyListWhenNoData() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("list", List.of());
        empty.put("total", 0L);
        when(productService.list(any(), eq(1), eq(20))).thenReturn(empty);

        Map<String, Object> response = controller.list(1, 20, "不存在的商品XYZ", null, null, null, null, null);

        assertEquals(0, response.get("code"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertEquals(0L, data.get("total"));
        assertTrue(((List<?>) data.get("list")).isEmpty());
    }
}
