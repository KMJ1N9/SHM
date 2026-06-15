package com.shm.common.model.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建举报请求 DTO（与 Node.js reportController.create 的 req.body 一致）
 *
 * <p>前端发送 snake_case JSON，JacksonConfig 反序列化默认驼峰，需显式映射。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {

    @NotNull
    @JsonProperty("reported_user_id")
    private Long reportedUserId;

    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("order_id")
    private Long orderId;

    @NotBlank
    private String type;

    @NotBlank
    private String description;

    /** 证据图片 URL 列表 */
    @JsonProperty("evidence_images")
    private List<String> evidenceImages;
}
