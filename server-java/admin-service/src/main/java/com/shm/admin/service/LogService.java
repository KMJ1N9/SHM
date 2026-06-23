package com.shm.admin.service;

import com.shm.admin.mapper.AdminLogMapper;
import com.shm.common.model.dto.admin.AdminLogRequest;
import com.shm.common.model.dto.admin.LogQueryRequest;
import com.shm.common.model.entity.AdminLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(LogService.class);

    private final AdminLogMapper adminLogMapper;

    public LogService(AdminLogMapper adminLogMapper) {
        this.adminLogMapper = adminLogMapper;
    }

    /**
     * 写入管理员操作日志（Phase 13 — 跨服务事务分支）
     *
     * <p>由 core-service 的 OrderService.confirm() 通过 AdminLogFeign 调用。
     */
    public long createLog(AdminLogRequest request) {
        AdminLog adminLog = AdminLog.builder()
                .adminId(request.getAdminId())
                .action(request.getAction())
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .reason(request.getReason())
                .build();
        adminLogMapper.insert(adminLog);
        log.debug("审计日志写入: action={}, adminId={}, targetId={}",
                request.getAction(), request.getAdminId(), request.getTargetId());
        return adminLog.getId() != null ? adminLog.getId() : 0;
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
