package com.shm.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.admin.feign.CoreUserFeign;
import com.shm.admin.feign.ImConnectorFeign;
import com.shm.admin.mapper.AdminLogMapper;
import com.shm.admin.mapper.NotificationMapper;
import com.shm.admin.mapper.ReportMapper;
import com.shm.admin.mapper.UserMapper;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.dto.internal.PenaltyRequest;
import com.shm.common.model.entity.AdminLog;
import com.shm.common.model.entity.Notification;
import com.shm.common.model.entity.Report;
import com.shm.common.model.entity.User;
import io.seata.spring.annotation.GlobalTransactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端工单服务（与 Node.js services/admin.js 行为完全一致）
 *
 * <p>处理工单列表、受理、裁决（含事务内扣减信誉分 + 通知）。
 */
@Service
public class ReportAdminService {

    private static final Logger log = LoggerFactory.getLogger(ReportAdminService.class);

    /** 信誉分上限（与 Node.js config.credit.max 一致，默认 200） */
    @Value("${credit.max:200}")
    private int creditMax;

    private final ReportMapper reportMapper;
    private final UserMapper userMapper;
    private final NotificationMapper notificationMapper;
    private final AdminLogMapper adminLogMapper;
    private final ObjectMapper objectMapper;
    private final ImConnectorFeign imConnectorFeign;
    private final CoreUserFeign coreUserFeign;

    public ReportAdminService(ReportMapper reportMapper, UserMapper userMapper,
                               NotificationMapper notificationMapper,
                               AdminLogMapper adminLogMapper, ObjectMapper objectMapper,
                               ImConnectorFeign imConnectorFeign, CoreUserFeign coreUserFeign) {
        this.reportMapper = reportMapper;
        this.userMapper = userMapper;
        this.notificationMapper = notificationMapper;
        this.adminLogMapper = adminLogMapper;
        this.objectMapper = objectMapper;
        this.imConnectorFeign = imConnectorFeign;
        this.coreUserFeign = coreUserFeign;
    }

    /**
     * 工单列表（与 Node.js adminService.listTickets 一致）
     *
     * @param status   工单状态筛选（all/pending/processing/resolved）
     * @param type     举报类型筛选
     * @param page     页码
     * @param pageSize 每页条数（上限 50）
     */
    public Map<String, Object> listTickets(String status, String type, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Map<String, Object>> list = reportMapper.listWithFilters(status, type, offset, pageSize);
        long total = reportMapper.countWithFilters(status, type);
        return Map.of("list", list, "total", total, "page", page, "pageSize", pageSize);
    }

    /**
     * 受理工单（与 Node.js adminService.processTicket 一致）
     *
     * <p>将 pending → processing。
     */
    @Transactional
    public Map<String, Object> processTicket(Long ticketId, Long adminId) {
        Report ticket = reportMapper.findById(ticketId);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }
        if (!"pending".equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "工单已被其他人受理");
        }

        reportMapper.updateStatus(ticketId, "processing");

        // 记录管理员操作日志
        AdminLog adminLog = AdminLog.builder()
                .adminId(adminId)
                .action("process_ticket")
                .targetType("ticket")
                .targetId(ticketId)
                .build();
        adminLogMapper.insert(adminLog);

        log.info("工单受理: ticketId={}, adminId={}", ticketId, adminId);

        Map<String, Object> detail = reportMapper.findDetailById(ticketId);
        return detail != null ? detail : Map.of();
    }

    /**
     * 裁决工单（与 Node.js adminService.resolveTicket 完全一致）
     *
     * <p>事务内原子操作：工单裁决 + 处罚执行 + 通知双方 + 管理员日志。
     *
     * <h3>Phase 13 — Seata 全局事务</h3>
     * <p>{@code @GlobalTransactional} 将本方法注册为 Seata 全局事务入口。
     * admin-service 本地写操作（工单/通知/日志）为分支 1，
     * Feign 调用 core-service 执行用户处罚为分支 2。
     * 任一分支失败 → Seata TC 协调两阶段回滚。
     *
     * @param penalty "none"（仅说明）/ "deduct_credit"（扣减信誉分）/ "ban"（封禁用户）
     */
    @GlobalTransactional(timeoutMills = 300000, name = "resolve-ticket")
    @Transactional
    public Map<String, Object> resolveTicket(Long ticketId, Long adminId, String resolution,
                                              String penalty, int deductCredit) {
        Report ticket = reportMapper.findById(ticketId);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }
        if (!"processing".equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "仅已受理的工单可裁决");
        }

        // 1. 裁决工单
        reportMapper.resolve(ticketId, resolution);

        // 2. 执行处罚
        String reportedContent;
        String reporterContent; // 举报人收到的通知内容

        if ("ban".equals(penalty) || deductCredit > 0) {
            // Phase 13 — Seata 跨服务事务分支：Feign 调用 core-service 执行用户处罚
            PenaltyRequest penaltyReq = new PenaltyRequest(penalty, deductCredit, resolution, ticketId);
            Map<String, Object> penaltyResult = coreUserFeign.applyPenalty(ticket.getReportedUserId(), penaltyReq);

            int currentScore = 0;
            int previousScore = 0;
            if (penaltyResult != null) {
                int resultCode = ((Number) penaltyResult.getOrDefault("code", -1)).intValue();
                if (resultCode != 0) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "跨服务处罚失败: " + penaltyResult.get("message"));
                }
                if (penaltyResult.containsKey("data")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) penaltyResult.get("data");
                    if (data != null) {
                        previousScore = ((Number) data.getOrDefault("previousScore", 0)).intValue();
                        currentScore = ((Number) data.getOrDefault("currentScore", 0)).intValue();
                    }
                }
            }

            if ("ban".equals(penalty)) {
                reportedContent = "你的账号已被封禁，原因：" + resolution;
                reporterContent = "你举报的用户已被封禁";

                Map<String, Object> banMetadataMap = new LinkedHashMap<>();
                banMetadataMap.put("reason", resolution);
                banMetadataMap.put("action", "ban");
                banMetadataMap.put("ref_id", ticketId);
                banMetadataMap.put("previous_score", previousScore);
                String banMetadata = toJson(banMetadataMap);

                Notification banNotification = Notification.builder()
                        .userId(ticket.getReportedUserId())
                        .type("ban")
                        .title("账号封禁")
                        .content(reportedContent)
                        .isRead(false)
                        .metadata(banMetadata)
                        .build();
                notificationMapper.insert(banNotification);
            } else {
                reportedContent = "你的信誉分变更为：" + currentScore + "（-" + deductCredit + " 举报成立）";
                reporterContent = "你举报的用户已被处理，信誉分-" + deductCredit;

                Map<String, Object> metadataMap = new LinkedHashMap<>();
                metadataMap.put("delta", -deductCredit);
                metadataMap.put("reason", "举报成立");
                metadataMap.put("previous_score", previousScore);
                metadataMap.put("current_score", currentScore);
                metadataMap.put("ref_id", ticketId);
                String metadata = toJson(metadataMap);

                Notification notification = Notification.builder()
                        .userId(ticket.getReportedUserId())
                        .type("credit_change")
                        .title("信誉分变动")
                        .content(reportedContent)
                        .isRead(false)
                        .metadata(metadata)
                        .build();
                notificationMapper.insert(notification);
            }
        } else {
            // penalty=none：仅裁决说明，不执行处罚
            reportedContent = "举报已处理：" + resolution;
            reporterContent = "你提交的举报已处理完毕";
        }

        // 3. 通知被举报人（IM 推送，静默失败不影响事务）
        pushImMessage(ticket.getReportedUserId(), "举报处理通知", reportedContent);

        // 4. 通知举报人（计划 5.1.3：IM 通知双方）
        pushImMessage(ticket.getReporterId(), "举报处理通知", reporterContent);

        // 5. 记录管理员操作日志
        AdminLog adminLog = AdminLog.builder()
                .adminId(adminId)
                .action("resolve_ticket")
                .targetType("ticket")
                .targetId(ticketId)
                .reason("penalty=" + penalty + ", deductCredit=" + deductCredit
                        + ", resolution=" + (resolution.length() > 100 ? resolution.substring(0, 100) + "..." : resolution))
                .build();
        adminLogMapper.insert(adminLog);

        log.info("工单裁决: ticketId={}, adminId={}, penalty={}, deductCredit={}",
                ticketId, adminId, penalty, deductCredit);

        Map<String, Object> detail = reportMapper.findDetailById(ticketId);
        return detail != null ? detail : Map.of();
    }

    // ---- 辅助方法 ----

    /**
     * 通过 Feign 推送 IM 实时消息（静默失败，不影响调用方事务）
     *
     * <p>IM Connector 不可用时自动降级为仅站内通知（DB 已持久化）。
     */
    private void pushImMessage(Long userId, String title, String content) {
        try {
            Map<String, Object> result = imConnectorFeign.sendSystemMessage(
                    String.valueOf(userId), title, content, null);
            if (result != null && result.containsKey("code")) {
                int code = ((Number) result.get("code")).intValue();
                if (code != 0) {
                    log.warn("IM 推送失败: userId={}, code={}, message={}", userId, code, result.get("message"));
                }
            }
        } catch (Exception e) {
            log.warn("IM 推送异常（已降级为站内通知）: userId={}, error={}", userId, e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            return "{}";
        }
    }
}
