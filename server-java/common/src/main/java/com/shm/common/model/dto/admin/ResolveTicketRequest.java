package com.shm.common.model.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 裁决工单请求（与 Node.js adminController.resolveTicket 的 req.body 一致）
 *
 * <p>前端发送 snake_case JSON，JacksonConfig 反序列化默认驼峰，需显式映射。
 */
@Data
public class ResolveTicketRequest {

    /** 裁决结果描述，必填 */
    @NotBlank(message = "裁决结果不能为空")
    @Size(min = 1, max = 500, message = "裁决结果 1-500 字符")
    private String resolution;

    /** 处罚类型：none（仅裁决说明）、deduct_credit（扣减信誉分）、ban（封禁用户），可选（默认 none） */
    private String penalty = "none";

    /** 扣减信誉分值，0 表示不扣分，可选（默认 0）。仅 penalty=deduct_credit 时生效。上限 100（与 Node.js Joi max(100) 一致） */
    @Max(value = 100, message = "单次扣分上限 100")
    @JsonProperty("deduct_credit")
    private Integer deductCredit = 0;
}
