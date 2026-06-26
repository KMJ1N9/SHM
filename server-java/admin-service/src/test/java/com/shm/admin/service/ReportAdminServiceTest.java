package com.shm.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.admin.feign.CoreUserFeign;
import com.shm.admin.mapper.AdminLogMapper;
import com.shm.admin.mq.ReportEventPublisher;
import com.shm.admin.mapper.NotificationMapper;
import com.shm.admin.mapper.ReportMapper;
import com.shm.admin.mapper.UserMapper;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.entity.AdminLog;
import com.shm.common.model.entity.Notification;
import com.shm.common.model.entity.Report;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReportAdminService 单元测试（Phase 11 11.1.6）
 *
 * <p>覆盖工单列表/受理/裁决（含处罚：none/deduct_credit/ban），Mock Mapper + IM Feign 层。
 */
@ExtendWith(MockitoExtension.class)
class ReportAdminServiceTest {

    @Mock
    private ReportMapper reportMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private AdminLogMapper adminLogMapper;
    @Mock
    private ReportEventPublisher reportEventPublisher;
    @Mock
    private CoreUserFeign coreUserFeign;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ReportAdminService reportAdminService;

    @BeforeEach
    void setUp() {
        reportAdminService = new ReportAdminService(reportMapper, userMapper,
                notificationMapper, adminLogMapper, objectMapper,
                new ObjectProvider<ReportEventPublisher>() {
                    @Override public ReportEventPublisher getObject() { return reportEventPublisher; }
                },
                coreUserFeign);
    }

    // ============================================================
    // listTickets — 工单列表
    // ============================================================

    @Test
    void listTickets_withStatusFilter_shouldReturnPaginated() {
        Map<String, Object> row = Map.of("id", 1L, "status", "pending", "type", "描述不符");
        when(reportMapper.listWithFilters(eq("pending"), isNull(), eq(0), eq(20)))
                .thenReturn(List.of(row));
        when(reportMapper.countWithFilters(eq("pending"), isNull())).thenReturn(1L);

        Map<String, Object> result = reportAdminService.listTickets("pending", null, 1, 20);

        assertEquals(1L, result.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
        assertEquals(1, list.size());
    }

    @Test
    void listTickets_withTypeFilter_shouldPassToMapper() {
        when(reportMapper.listWithFilters(isNull(), eq("描述不符"), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(reportMapper.countWithFilters(isNull(), eq("描述不符"))).thenReturn(0L);

        reportAdminService.listTickets(null, "描述不符", 1, 20);

        verify(reportMapper).listWithFilters(isNull(), eq("描述不符"), eq(0), eq(20));
    }

    // ============================================================
    // processTicket — 受理工单
    // ============================================================

    @Test
    void processTicket_success_shouldUpdateStatus() {
        Report ticket = buildReport(1L, 10L, 20L, "pending");
        when(reportMapper.findById(1L)).thenReturn(ticket);

        Map<String, Object> detail = Map.of("id", 1L, "status", "processing");
        when(reportMapper.findDetailById(1L)).thenReturn(detail);

        Map<String, Object> result = reportAdminService.processTicket(1L, 1L);

        verify(reportMapper).updateStatus(1L, "processing");
        verify(adminLogMapper).insert(any(AdminLog.class));
        assertEquals("processing", result.get("status"));
    }

    @Test
    void processTicket_notFound_shouldThrow() {
        when(reportMapper.findById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reportAdminService.processTicket(999L, 1L));

        assertEquals(ErrorCode.REPORT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void processTicket_alreadyProcessing_shouldThrow() {
        Report ticket = buildReport(1L, 10L, 20L, "processing");
        when(reportMapper.findById(1L)).thenReturn(ticket);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reportAdminService.processTicket(1L, 1L));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void processTicket_resolved_shouldThrow() {
        // 已裁决的工单不能被重新受理
        Report ticket = buildReport(1L, 10L, 20L, "resolved");
        when(reportMapper.findById(1L)).thenReturn(ticket);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reportAdminService.processTicket(1L, 1L));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID.getCode(), ex.getCode());
    }

    // ============================================================
    // resolveTicket — 裁决工单
    // ============================================================

    @Test
    void resolveTicket_nonePenalty_shouldOnlyResolve() {
        Report ticket = buildReport(1L, 10L, 20L, "processing");
        when(reportMapper.findById(1L)).thenReturn(ticket);

        Map<String, Object> detail = Map.of("id", 1L, "status", "resolved");
        when(reportMapper.findDetailById(1L)).thenReturn(detail);

        // IM 推送正常（举报人 + 被举报人各一条）
        doNothing().when(reportEventPublisher).publishReportEvent(any());

        Map<String, Object> result = reportAdminService.resolveTicket(1L, 1L,
                "情况说明，不做处罚", "none", 0);

        verify(reportMapper).resolve(1L, "情况说明，不做处罚");
        // Phase 13: 不应调用跨服务处罚
        verify(coreUserFeign, never()).applyPenalty(anyLong(), any());
        verify(notificationMapper, never()).insert(any());
        assertEquals("resolved", result.get("status"));
    }

    @Test
    void resolveTicket_deductCredit_shouldDeductAndNotify() {
        Report ticket = buildReport(1L, 10L, 20L, "processing");
        when(reportMapper.findById(1L)).thenReturn(ticket);

        // Phase 13: Feign 调用 core-service 执行处罚
        Map<String, Object> penaltyData = Map.of(
                "userId", 20L,
                "previousScore", 80,
                "currentScore", 70,
                "status", "active"
        );
        when(coreUserFeign.applyPenalty(eq(20L), any()))
                .thenReturn(Map.of("code", 0, "message", "ok", "data", penaltyData));

        Map<String, Object> detail = Map.of("id", 1L, "status", "resolved");
        when(reportMapper.findDetailById(1L)).thenReturn(detail);

        doNothing().when(reportEventPublisher).publishReportEvent(any());

        Map<String, Object> result = reportAdminService.resolveTicket(1L, 1L,
                "违规发布", "deduct_credit", 10);

        verify(reportMapper).resolve(1L, "违规发布");
        // Phase 13: 验证跨服务处罚调用
        verify(coreUserFeign).applyPenalty(eq(20L), any());
        verify(notificationMapper).insert(any(Notification.class));
        assertEquals("resolved", result.get("status"));
    }

    @Test
    void resolveTicket_banPenalty_shouldBanAndNotify() {
        Report ticket = buildReport(1L, 10L, 20L, "processing");
        when(reportMapper.findById(1L)).thenReturn(ticket);

        // Phase 13: Feign 调用 core-service 执行封禁
        Map<String, Object> penaltyData = Map.of(
                "userId", 20L,
                "previousScore", 80,
                "currentScore", 0,
                "status", "banned"
        );
        when(coreUserFeign.applyPenalty(eq(20L), any()))
                .thenReturn(Map.of("code", 0, "message", "ok", "data", penaltyData));

        Map<String, Object> detail = Map.of("id", 1L, "status", "resolved");
        when(reportMapper.findDetailById(1L)).thenReturn(detail);

        doNothing().when(reportEventPublisher).publishReportEvent(any());

        Map<String, Object> result = reportAdminService.resolveTicket(1L, 1L,
                "严重违规", "ban", 0);

        verify(reportMapper).resolve(1L, "严重违规");
        // Phase 13: 验证跨服务封禁调用
        verify(coreUserFeign).applyPenalty(eq(20L), any());
        verify(notificationMapper).insert(argThat(n ->
                "ban".equals(n.getType()) && n.getUserId().equals(20L)));
        assertEquals("resolved", result.get("status"));
    }

    @Test
    void resolveTicket_notFound_shouldThrow() {
        when(reportMapper.findById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reportAdminService.resolveTicket(999L, 1L, "resolved", "none", 0));

        assertEquals(ErrorCode.REPORT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void resolveTicket_notProcessing_shouldThrow() {
        // 只有 processing 状态才能裁决
        Report ticket = buildReport(1L, 10L, 20L, "pending");
        when(reportMapper.findById(1L)).thenReturn(ticket);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reportAdminService.resolveTicket(1L, 1L, "resolved", "none", 0));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void resolveTicket_resolvedAlready_shouldThrow() {
        // 已裁决的工单不能再次裁决
        Report ticket = buildReport(1L, 10L, 20L, "resolved");
        when(reportMapper.findById(1L)).thenReturn(ticket);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reportAdminService.resolveTicket(1L, 1L, "again", "none", 0));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void resolveTicket_imPushFail_shouldNotAffectTransaction() {
        Report ticket = buildReport(1L, 10L, 20L, "processing");
        when(reportMapper.findById(1L)).thenReturn(ticket);

        Map<String, Object> detail = Map.of("id", 1L, "status", "resolved");
        when(reportMapper.findDetailById(1L)).thenReturn(detail);

        doThrow(new RuntimeException("IM 服务不可用")).when(reportEventPublisher).publishReportEvent(any());

        // 不应抛出异常
        Map<String, Object> result = reportAdminService.resolveTicket(1L, 1L,
                "正常裁决", "none", 0);

        assertEquals("resolved", result.get("status"));
        verify(reportMapper).resolve(1L, "正常裁决");
    }

    // ---- 辅助 ----

    private Report buildReport(Long id, Long reporterId, Long reportedUserId, String status) {
        return Report.builder()
                .id(id).reporterId(reporterId).reportedUserId(reportedUserId)
                .productId(100L).orderId(200L)
                .type("描述不符").description("商品与描述不符").evidenceImages("[]")
                .status(status)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }
}
