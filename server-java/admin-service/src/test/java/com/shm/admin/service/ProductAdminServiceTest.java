package com.shm.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.admin.feign.ImConnectorFeign;
import com.shm.admin.mapper.AdminLogMapper;
import com.shm.admin.mapper.ProductMapper;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.entity.AdminLog;
import com.shm.common.model.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProductAdminService 单元测试（Phase 11 11.1.6）
 *
 * <p>覆盖管理端商品列表（含嵌套 seller/封面提取）/强制下架，Mock Mapper + IM Feign 层。
 */
@ExtendWith(MockitoExtension.class)
class ProductAdminServiceTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private AdminLogMapper adminLogMapper;
    @Mock
    private ImConnectorFeign imConnectorFeign;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProductAdminService productAdminService;

    @BeforeEach
    void setUp() {
        productAdminService = new ProductAdminService(productMapper, adminLogMapper,
                objectMapper, imConnectorFeign);
    }

    // ============================================================
    // listAllProducts — 管理端商品列表
    // ============================================================

    @Test
    void listAllProducts_shouldReturnWithSellerNesting() {
        Map<String, Object> row = Map.of(
                "id", 1L, "title", "iPhone",
                "seller_id", 10L, "seller_nickname", "卖家A",
                "seller_avatar", "/av.png", "seller_credit_score", 90,
                "images", "[\"a.jpg\",\"b.jpg\"]",
                "status", "active"
        );
        when(productMapper.listAll(isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(List.of(row));
        when(productMapper.countAll(isNull(), isNull())).thenReturn(1L);

        Map<String, Object> result = productAdminService.listAllProducts(null, null, 1, 20);

        assertEquals(1L, result.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
        assertEquals(1, list.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> seller = (Map<String, Object>) list.get(0).get("seller");
        assertNotNull(seller);
        assertEquals(10L, seller.get("id"));
        assertEquals("卖家A", seller.get("nickname"));
        assertEquals("a.jpg", list.get(0).get("cover_image"));
    }

    @Test
    void listAllProducts_emptyImages_shouldReturnNullCover() {
        // Map.of() 不允许 null 值，使用 LinkedHashMap
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", 1L);
        row.put("title", "商品");
        row.put("seller_id", 10L);
        row.put("seller_nickname", "卖家");
        row.put("seller_avatar", null);
        row.put("seller_credit_score", 80);
        row.put("images", null);
        row.put("status", "active");
        when(productMapper.listAll(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));
        when(productMapper.countAll(any(), any())).thenReturn(1L);

        Map<String, Object> result = productAdminService.listAllProducts(null, null, 1, 20);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
        assertNull(list.get(0).get("cover_image"));
    }

    @Test
    void listAllProducts_emptyResult_shouldReturnZeroTotal() {
        when(productMapper.listAll(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(productMapper.countAll(any(), any())).thenReturn(0L);

        Map<String, Object> result = productAdminService.listAllProducts(null, null, 1, 20);

        assertEquals(0L, result.get("total"));
    }

    @Test
    void listAllProducts_withStatusFilter_shouldPassToMapper() {
        when(productMapper.listAll(eq("off_shelf"), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(productMapper.countAll(eq("off_shelf"), isNull())).thenReturn(0L);

        productAdminService.listAllProducts("off_shelf", null, 1, 20);

        verify(productMapper).listAll(eq("off_shelf"), isNull(), eq(0), eq(20));
    }

    // ============================================================
    // offShelfProduct — 下架商品
    // ============================================================

    @Test
    void offShelfProduct_success_shouldUpdateAndLog() {
        Product product = buildProduct(1L, 10L, "iPhone", "active");
        when(productMapper.findById(1L)).thenReturn(product);

        Product updated = buildProduct(1L, 10L, "iPhone", "off_shelf");
        when(productMapper.findById(1L)).thenReturn(product)
                .thenReturn(updated);

        // IM 推送成功
        when(imConnectorFeign.sendSystemMessage(anyString(), anyString(), contains("iPhone"), isNull()))
                .thenReturn(Map.of("code", 0));

        Map<String, Object> result = productAdminService.offShelfProduct(1L, 1L);

        verify(productMapper).updateStatus(1L, "off_shelf");
        verify(adminLogMapper).insert(any(AdminLog.class));
        assertEquals("off_shelf", result.get("status"));
    }

    @Test
    void offShelfProduct_notFound_shouldThrow() {
        when(productMapper.findById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                productAdminService.offShelfProduct(999L, 1L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void offShelfProduct_imPushFail_shouldNotAffectTransaction() {
        Product product = buildProduct(1L, 10L, "iPhone", "active");
        when(productMapper.findById(1L)).thenReturn(product);

        Product updated = buildProduct(1L, 10L, "iPhone", "off_shelf");
        when(productMapper.findById(1L)).thenReturn(product)
                .thenReturn(updated);

        // IM 推送抛出异常（应被静默捕获）
        when(imConnectorFeign.sendSystemMessage(anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("IM 服务不可用"));

        // 不应抛出异常，事务继续
        Map<String, Object> result = productAdminService.offShelfProduct(1L, 1L);

        assertEquals("off_shelf", result.get("status"));
        verify(productMapper).updateStatus(1L, "off_shelf");
    }

    // ---- 辅助 ----

    private Product buildProduct(Long id, Long sellerId, String title, String status) {
        return Product.builder()
                .id(id).sellerId(sellerId).title(title)
                .description("描述").category("电子产品").condition("良好")
                .originalPrice(new BigDecimal("5000")).price(new BigDecimal("3000"))
                .status(status)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }
}
