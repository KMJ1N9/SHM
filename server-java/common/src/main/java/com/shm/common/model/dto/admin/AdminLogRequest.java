package com.shm.common.model.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 管理员操作日志写入请求（Phase 13 — 跨服务事务）
 *
 * <p>由 core-service 通过 Feign 调用 admin-service 内部 API 时传递。
 * 校验注解由 InternalAdminLogController 的 {@code @Valid} 触发。
 */
public class AdminLogRequest {

    @NotNull(message = "操作人 ID 不能为空")
    private Long adminId;

    @NotBlank(message = "操作类型不能为空")
    private String action;

    @NotBlank(message = "目标类型不能为空")
    private String targetType;

    @NotNull(message = "目标 ID 不能为空")
    private Long targetId;

    private String reason;

    public AdminLogRequest() {}

    public AdminLogRequest(Long adminId, String action, String targetType, Long targetId, String reason) {
        this.adminId = adminId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
    }

    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
