package com.shm.admin.service;

import com.shm.admin.mapper.AdminLogMapper;
import com.shm.common.model.dto.admin.LogQueryRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审计日志服务（与 Node.js services/admin.js 审计日志部分行为完全一致）
 *
 * <p>提供操作审计日志列表查询（支持时间范围/操作类型筛选）。
 */
@Service
public class LogService {

    private final AdminLogMapper adminLogMapper;

    public LogService(AdminLogMapper adminLogMapper) {
        this.adminLogMapper = adminLogMapper;
    }

    /**
     * 操作审计日志列表（与 Node.js adminService.listLogs 一致）
     */
    public Map<String, Object> listLogs(LogQueryRequest query) {
        String action = query.getAction();
        String targetType = query.getTargetType();
        LocalDateTime start = query.getStart();
        LocalDateTime end = query.getEnd();
        int page = query.getPage();
        int pageSize = query.getPageSize();

        // 若 end 不含时间分量（如 '2026-06-11T00:00:00'），补齐 23:59:59 确保覆盖全天
        // 与 Node.js listAdminLogs 的 end_date 处理逻辑一致
        LocalDateTime effectiveEnd = end;
        if (end != null && end.getHour() == 0 && end.getMinute() == 0 && end.getSecond() == 0) {
            effectiveEnd = end.withHour(23).withMinute(59).withSecond(59);
        }

        int offset = (page - 1) * pageSize;
        List<Map<String, Object>> list = adminLogMapper.listWithFilters(action, targetType, offset, pageSize, start, effectiveEnd);
        long total = adminLogMapper.countWithFilters(action, targetType, start, effectiveEnd);

        return Map.of("list", list, "total", total, "page", page, "pageSize", pageSize);
    }
}
