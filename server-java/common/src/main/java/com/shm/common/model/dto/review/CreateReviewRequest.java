package com.shm.common.model.dto.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建评价请求 DTO（与 Node.js reviewController.create 的 req.body 一致）
 *
 * <p>前端发送 snake_case JSON，JacksonConfig 反序列化默认驼峰，需显式映射。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewRequest {

    @NotNull
    @JsonProperty("order_id")
    private Long orderId;

    @NotNull
    @JsonProperty("reviewee_id")
    private Long revieweeId;

    @NotNull @Min(1) @Max(5)
    @JsonProperty("communication_score")
    private Integer communicationScore;

    @NotNull @Min(1) @Max(5)
    @JsonProperty("punctuality_score")
    private Integer punctualityScore;

    @NotNull @Min(1) @Max(5)
    @JsonProperty("accuracy_score")
    private Integer accuracyScore;

    private String comment;
}
