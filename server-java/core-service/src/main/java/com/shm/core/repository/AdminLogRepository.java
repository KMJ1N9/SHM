package com.shm.core.repository;

import com.shm.common.model.entity.AdminLog;
import com.shm.core.mapper.AdminLogMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理日志 Repository（封装 AdminLogMapper）
 */
@Repository
public class AdminLogRepository {

    private final AdminLogMapper mapper;

    public AdminLogRepository(AdminLogMapper mapper) {
        this.mapper = mapper;
    }

    public AdminLog insert(AdminLog log) {
        mapper.insert(log);
        return log;
    }

    public List<AdminLog> listByAdmin(Long adminId, int offset, int limit) {
        return mapper.listByAdmin(adminId, offset, limit);
    }

    public List<AdminLog> listWithFilters(String action, String targetType, int offset, int limit,
                                           LocalDateTime start, LocalDateTime end) {
        return mapper.listWithFilters(action, targetType, offset, limit, start, end);
    }
}
