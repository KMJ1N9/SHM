package com.shm.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.dto.report.CreateReportRequest;
import com.shm.common.model.entity.Order;
import com.shm.common.model.entity.Report;
import com.shm.common.model.entity.User;
import com.shm.core.mq.OrderEventPublisher;
import com.shm.core.repository.NotificationRepository;
import com.shm.core.repository.OrderRepository;
import com.shm.core.repository.ProductRepository;
import com.shm.core.repository.ReportRepository;
import com.shm.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReportService 单元测试（Phase 11 11.1.5g）
 *
 * <p>覆盖举报创建/列表/详情，Mock Repository 层。
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepo;
    @Mock
    private OrderRepository orderRepo;
    @Mock
    private ProductRepository productRepo;
    @Mock
    private UserRepository userRepo;
    @Mock
    private NotificationRepository notificationRepo;
    @Mock
    private OrderEventPublisher orderEventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepo, orderRepo, productRepo,
                userRepo, notificationRepo,
                new ObjectProvider<OrderEventPublisher>() {
                    @Override public OrderEventPublisher getObject() { return orderEventPublisher; }
                },
                objectMapper);
        // 默认无管理员，避免 notifyAdmins 产生 NPE
        lenient().when(userRepo.findAdminUsers()).thenReturn(List.of());
    }

    // ============================================================
    // create — 创建举报
    // ============================================================

    @Test
    void create_success_shouldInsertAndReturnReport() {
        // Given
        CreateReportRequest req = buildRequest(1L, 2L, null, null, "user", "恶意行为");
        when(reportRepo.countActiveByReporter(eq(1L), eq(2L), isNull(), isNull())).thenReturn(0L);

        Report inserted = Report.builder()
                .id(10L).reporterId(1L).reportedUserId(2L)
                .type("user").description("恶意行为")
                .evidenceImages("[]").status("pending").build();
        when(reportRepo.insert(any(Report.class))).thenReturn(inserted);

        // When
        Map<String, Object> result = reportService.create(1L, req);

        // Then
        assertNotNull(result);
        assertEquals(10L, result.get("id"));
        assertEquals(1L, result.get("reporter_id"));
        assertEquals(2L, result.get("reported_user_id"));
        assertEquals("user", result.get("type"));
        assertEquals("pending", result.get("status"));

        verify(reportRepo).insert(any(Report.class));
    }

    @Test
    void create_selfReport_shouldThrowBusinessException() {
        CreateReportRequest req = buildRequest(1L, 1L, null, null, "user", "自己");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reportService.create(1L, req));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("不能举报自己"));
    }

    @Test
    void create_duplicateReport_shouldThrowBusinessException() {
        CreateReportRequest req = buildRequest(1L, 2L, 10L, null, "product", "重复");
        when(reportRepo.countActiveByReporter(eq(1L), eq(2L), eq(10L), isNull()))
                .thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reportService.create(1L, req));

        assertEquals(ErrorCode.DUPLICATE_REPORT.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("已提交过"));
    }

    @Test
    void create_orderNotFound_shouldThrowBusinessException() {
        CreateReportRequest req = buildRequest(1L, 2L, null, 999L, "order", "投诉");
        when(reportRepo.countActiveByReporter(eq(1L), eq(2L), isNull(), eq(999L))).thenReturn(0L);
        when(orderRepo.findById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reportService.create(1L, req));

        assertEquals(ErrorCode.ORDER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void create_notOrderParticipant_shouldThrowBusinessException() {
        CreateReportRequest req = buildRequest(1L, 2L, null, 5L, "order", "投诉");
        when(reportRepo.countActiveByReporter(eq(1L), eq(2L), isNull(), eq(5L))).thenReturn(0L);

        Order order = Order.builder().id(5L).buyerId(3L).sellerId(4L).build();
        when(orderRepo.findById(5L)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reportService.create(1L, req));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("未参与该订单"));
    }

    @Test
    void create_productNotFound_shouldThrowBusinessException() {
        CreateReportRequest req = buildRequest(1L, 2L, 888L, null, "product", "投诉");
        when(reportRepo.countActiveByReporter(eq(1L), eq(2L), eq(888L), isNull())).thenReturn(0L);
        when(productRepo.findById(888L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reportService.create(1L, req));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void create_emptyEvidence_shouldDefaultToEmptyArray() {
        CreateReportRequest req = buildRequest(1L, 2L, null, null, "user", "test");
        req.setEvidenceImages(null);
        when(reportRepo.countActiveByReporter(eq(1L), eq(2L), isNull(), isNull())).thenReturn(0L);

        Report inserted = Report.builder()
                .id(11L).reporterId(1L).reportedUserId(2L)
                .type("user").description("test")
                .evidenceImages("[]").status("pending").build();
        when(reportRepo.insert(any(Report.class))).thenReturn(inserted);

        Map<String, Object> result = reportService.create(1L, req);

        assertNotNull(result);
        verify(reportRepo).insert(argThat(r -> "[]".equals(r.getEvidenceImages())));
    }

    // ============================================================
    // list — 举报列表
    // ============================================================

    @Test
    void list_shouldReturnPaginatedReports() {
        List<Report> reports = List.of(
                Report.builder().id(1L).reporterId(10L).reportedUserId(20L)
                        .type("user").description("test").evidenceImages("[]")
                        .status("pending").build()
        );
        when(reportRepo.listWithFilters(eq(10L), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(reports);
        when(reportRepo.countWithFilters(eq(10L), isNull(), isNull())).thenReturn(1L);

        Map<String, Object> result = reportService.list(10L, 1, 20);

        assertEquals(1L, result.get("total"));
        assertEquals(1, result.get("page"));
        assertEquals(20, result.get("pageSize"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
        assertEquals(1, list.size());
        assertEquals(1L, list.get(0).get("id"));
    }

    // ============================================================
    // detail — 举报详情
    // ============================================================

    @Test
    void detail_asReporter_shouldReturnFullDetail() {
        // Given
        Report report = Report.builder()
                .id(1L).reporterId(10L).reportedUserId(20L)
                .type("user").description("恶意").evidenceImages("[]")
                .status("pending").build();
        when(reportRepo.findById(1L)).thenReturn(report);

        User reporter = User.builder().id(10L).nickname("举报人").avatar("/a.png").build();
        User reported = User.builder().id(20L).nickname("被举报人").avatar("/b.png").build();
        when(userRepo.findByIds(anySet())).thenReturn(List.of(reporter, reported));

        // When
        Map<String, Object> result = reportService.detail(1L, 10L, "user");

        // Then
        assertEquals("举报人", result.get("reporter_nickname"));
        assertEquals("被举报人", result.get("reported_user_nickname"));
    }

    @Test
    void detail_asCsUser_shouldReturnFullDetail() {
        Report report = Report.builder()
                .id(2L).reporterId(10L).reportedUserId(20L)
                .type("product").description("假货").evidenceImages("[]")
                .status("pending").build();
        when(reportRepo.findById(2L)).thenReturn(report);

        User reporter = User.builder().id(10L).nickname("买家A").build();
        User reported = User.builder().id(20L).nickname("卖家B").build();
        when(userRepo.findByIds(anySet())).thenReturn(List.of(reporter, reported));

        // CS 用户不是举报人，但角色为 cs
        Map<String, Object> result = reportService.detail(2L, 99L, "cs");

        assertNotNull(result);
        assertEquals("买家A", result.get("reporter_nickname"));
    }

    @Test
    void detail_asUnauthorized_shouldThrowBusinessException() {
        Report report = Report.builder()
                .id(3L).reporterId(10L).reportedUserId(20L).build();
        when(reportRepo.findById(3L)).thenReturn(report);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reportService.detail(3L, 50L, "user"));

        assertEquals(ErrorCode.NOT_OWNER.getCode(), ex.getCode());
    }

    @Test
    void detail_reportNotFound_shouldThrowBusinessException() {
        when(reportRepo.findById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reportService.detail(999L, 10L, "user"));

        assertEquals(ErrorCode.REPORT_NOT_FOUND.getCode(), ex.getCode());
    }

    // ---- 辅助 ----

    private CreateReportRequest buildRequest(Long reporterId, Long reportedUserId,
                                              Long productId, Long orderId,
                                              String type, String description) {
        CreateReportRequest req = new CreateReportRequest();
        req.setReportedUserId(reportedUserId);
        req.setProductId(productId);
        req.setOrderId(orderId);
        req.setType(type);
        req.setDescription(description);
        return req;
    }
}
