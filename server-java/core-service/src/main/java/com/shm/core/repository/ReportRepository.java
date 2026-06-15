package com.shm.core.repository;

import com.shm.common.model.entity.Report;
import com.shm.core.mapper.ReportMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 举报 Repository（封装 ReportMapper，对应 Node.js repository/report.js）
 */
@Repository
public class ReportRepository {

    private final ReportMapper mapper;

    public ReportRepository(ReportMapper mapper) {
        this.mapper = mapper;
    }

    public Report insert(Report report) {
        mapper.insert(report);
        return report;
    }

    public Report findById(Long id) {
        return mapper.findById(id);
    }

    public List<Report> listWithFilters(Long reporterId, String status, String type, int offset, int limit) {
        return mapper.listWithFilters(reporterId, status, type, offset, limit);
    }

    public long countWithFilters(Long reporterId, String status, String type) {
        return mapper.countWithFilters(reporterId, status, type);
    }

    public int updateStatus(Long id, String status, String resolution) {
        return mapper.updateStatus(id, status, resolution);
    }

    /** 检查是否存在未处理的重复举报（返回 >0 表示有重复） */
    public long countActiveByReporter(Long reporterId, Long reportedUserId, Long productId, Long orderId) {
        return mapper.countActiveByReporter(reporterId, reportedUserId, productId, orderId);
    }
}
