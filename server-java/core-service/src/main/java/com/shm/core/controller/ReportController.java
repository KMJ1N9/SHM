package com.shm.core.controller;

import com.shm.common.model.dto.report.CreateReportRequest;
import com.shm.common.util.ResponseBuilder;
import com.shm.core.security.CurrentUser;
import com.shm.core.security.UserPrincipal;
import com.shm.core.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 举报控制器（与 Node.js controllers/report.js 行为完全一致）
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * POST /api/reports — 创建举报
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@CurrentUser UserPrincipal user,
                                      @Valid @RequestBody CreateReportRequest request) {
        return ResponseBuilder.ok(reportService.create(user.getUserId(), request));
    }

    /**
     * GET /api/reports — 我的举报列表
     */
    @GetMapping
    public Map<String, Object> list(@CurrentUser UserPrincipal user,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        pageSize = Math.min(pageSize, 50);
        return ResponseBuilder.ok(reportService.list(user.getUserId(), page, pageSize));
    }

    /**
     * GET /api/reports/:id — 举报详情
     */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id,
                                      @CurrentUser UserPrincipal user) {
        return ResponseBuilder.ok(reportService.detail(id, user.getUserId(), user.getRole()));
    }
}
