package com.shm.admin.controller;

import com.shm.admin.security.CurrentUser;
import com.shm.admin.security.UserPrincipal;
import com.shm.admin.service.ReportAdminService;
import com.shm.common.model.dto.admin.ResolveTicketRequest;
import com.shm.common.util.ResponseBuilder;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 工单管理控制器（与 Node.js adminController 工单部分行为完全一致）
 *
 * <p>客服/管理员均可操作。
 */
@RestController
@RequestMapping("/api/admin/tickets")
public class TicketController {

    private final ReportAdminService reportAdminService;

    public TicketController(ReportAdminService reportAdminService) {
        this.reportAdminService = reportAdminService;
    }

    /**
     * GET /api/admin/tickets — 工单列表
     */
    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "pending") String status,
                                     @RequestParam(defaultValue = "all") String type,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int pageSize) {
        pageSize = Math.min(pageSize, 50);
        return ResponseBuilder.ok(reportAdminService.listTickets(status, type, page, pageSize));
    }

    /**
     * PUT /api/admin/tickets/:id/process — 受理工单
     */
    @PutMapping("/{id}/process")
    public Map<String, Object> process(@PathVariable Long id,
                                       @CurrentUser UserPrincipal user) {
        return ResponseBuilder.ok(reportAdminService.processTicket(id, user.getUserId()));
    }

    /**
     * PUT /api/admin/tickets/:id/resolve — 裁决工单
     */
    @PutMapping("/{id}/resolve")
    public Map<String, Object> resolve(@PathVariable Long id,
                                       @CurrentUser UserPrincipal user,
                                       @Valid @RequestBody ResolveTicketRequest request) {
        return ResponseBuilder.ok(reportAdminService.resolveTicket(
                id, user.getUserId(), request.getResolution(), request.getPenalty(), request.getDeductCredit()));
    }
}
