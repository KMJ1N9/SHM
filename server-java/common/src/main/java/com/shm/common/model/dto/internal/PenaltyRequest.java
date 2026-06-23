package com.shm.common.model.dto.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 用户处罚请求（Phase 13 — 跨服务事务）
 *
 * <p>由 admin-service 通过 Feign 调用 core-service 内部 API 时传递。
 * 校验注解由 InternalUserController 的 {@code @Valid} 触发。
 * core-service 的 InternalUserController 根据 penalty 类型执行：
 * <ul>
 *   <li>{@code "deduct_credit"} — 扣减信誉分（deductCredit 必须 > 0）</li>
 *   <li>{@code "ban"} — 封禁用户（status = banned + tokenVersion +1）</li>
 * </ul>
 */
public class PenaltyRequest {

    /** 处罚类型：deduct_credit / ban */
    @NotBlank(message = "处罚类型不能为空")
    private String penalty;

    /** 扣减信誉分值（仅 deduct_credit 时有效） */
    private int deductCredit;

    /** 处罚原因/裁决说明 */
    private String reason;

    /** 关联的工单 ID（用于通知关联） */
    @NotNull(message = "工单 ID 不能为空")
    private Long ticketId;

    public PenaltyRequest() {}

    public PenaltyRequest(String penalty, int deductCredit, String reason, Long ticketId) {
        this.penalty = penalty;
        this.deductCredit = deductCredit;
        this.reason = reason;
        this.ticketId = ticketId;
    }

    public String getPenalty() { return penalty; }
    public void setPenalty(String penalty) { this.penalty = penalty; }

    public int getDeductCredit() { return deductCredit; }
    public void setDeductCredit(int deductCredit) { this.deductCredit = deductCredit; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
}
