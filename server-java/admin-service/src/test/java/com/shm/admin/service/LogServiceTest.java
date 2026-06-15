package com.shm.admin.service;

import com.shm.admin.mapper.AdminLogMapper;
import com.shm.common.model.dto.admin.LogQueryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LogService 单元测试（Phase 11 11.1.6）
 *
 * <p>覆盖审计日志列表查询（含日期范围调整逻辑），Mock AdminLogMapper 层。
 */
@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    @Mock
    private AdminLogMapper adminLogMapper;

    private LogService logService;

    @BeforeEach
    void setUp() {
        logService = new LogService(adminLogMapper);
    }

    // ============================================================
    // listLogs — 审计日志列表
    // ============================================================

    @Test
    void listLogs_withFilters_shouldReturnPaginatedResults() {
        LogQueryRequest query = new LogQueryRequest();
        query.setAction("ban");
        query.setTargetType("user");
        query.setPage(1);
        query.setPageSize(10);

        List<Map<String, Object>> rows = List.of(
                Map.of("id", 1L, "action", "ban", "target_type", "user")
        );
        when(adminLogMapper.listWithFilters(eq("ban"), eq("user"), eq(0), eq(10), isNull(), isNull()))
                .thenReturn(rows);
        when(adminLogMapper.countWithFilters(eq("ban"), eq("user"), isNull(), isNull()))
                .thenReturn(1L);

        Map<String, Object> result = logService.listLogs(query);

        assertEquals(1L, result.get("total"));
        assertEquals(1, result.get("page"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
        assertEquals(1, list.size());
    }

    @Test
    void listLogs_endAtMidnight_shouldAdjustToEndOfDay() {
        // 当 end 为 00:00:00 时，应补齐到 23:59:59 — 确保覆盖全天
        LogQueryRequest query = new LogQueryRequest();
        query.setEnd(LocalDateTime.of(2026, 6, 11, 0, 0, 0));
        query.setPage(1);
        query.setPageSize(20);

        when(adminLogMapper.listWithFilters(any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(List.of());
        when(adminLogMapper.countWithFilters(any(), any(), any(), any()))
                .thenReturn(0L);

        logService.listLogs(query);

        // 验证传给 mapper 的 end 已被调整为 23:59:59
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(adminLogMapper).listWithFilters(any(), any(), anyInt(), anyInt(), any(), endCaptor.capture());
        LocalDateTime capturedEnd = endCaptor.getValue();
        assertEquals(23, capturedEnd.getHour());
        assertEquals(59, capturedEnd.getMinute());
        assertEquals(59, capturedEnd.getSecond());
    }

    @Test
    void listLogs_endAtNoon_shouldNotAdjustEnd() {
        // 当 end 为具体时间（非 00:00:00）时，不应调整
        LogQueryRequest query = new LogQueryRequest();
        query.setEnd(LocalDateTime.of(2026, 6, 11, 12, 0, 0));
        query.setPage(1);
        query.setPageSize(20);

        when(adminLogMapper.listWithFilters(any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(List.of());
        when(adminLogMapper.countWithFilters(any(), any(), any(), any()))
                .thenReturn(0L);

        logService.listLogs(query);

        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(adminLogMapper).listWithFilters(any(), any(), anyInt(), anyInt(), any(), endCaptor.capture());
        LocalDateTime capturedEnd = endCaptor.getValue();
        assertEquals(12, capturedEnd.getHour());
        assertEquals(0, capturedEnd.getMinute());
    }

    @Test
    void listLogs_nullEnd_shouldPassNullToMapper() {
        LogQueryRequest query = new LogQueryRequest();
        query.setPage(1);
        query.setPageSize(20);

        when(adminLogMapper.listWithFilters(any(), any(), anyInt(), anyInt(), any(), isNull()))
                .thenReturn(List.of());
        when(adminLogMapper.countWithFilters(any(), any(), any(), isNull()))
                .thenReturn(0L);

        Map<String, Object> result = logService.listLogs(query);

        assertEquals(0L, result.get("total"));
    }

    @Test
    void listLogs_page2_shouldCalculateOffsetCorrectly() {
        LogQueryRequest query = new LogQueryRequest();
        query.setPage(3);
        query.setPageSize(15);

        when(adminLogMapper.listWithFilters(isNull(), isNull(), eq(30), eq(15), isNull(), isNull()))
                .thenReturn(List.of());
        when(adminLogMapper.countWithFilters(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(0L);

        logService.listLogs(query);

        verify(adminLogMapper).listWithFilters(isNull(), isNull(), eq(30), eq(15), isNull(), isNull());
    }
}
