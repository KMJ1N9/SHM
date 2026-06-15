package com.shm.common.model.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 刷新请求
 *
 * <p>前端发送 snake_case 字段名（refresh_token），Jackson 命名策略仅处理序列化（输出），
 * 反序列化（输入）仍使用 Java 字段名（camelCase），因此必须显式声明 @JsonProperty。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequest {

    /** 已过期的 Refresh Token */
    @NotBlank(message = "refresh_token 不能为空")
    @JsonProperty("refresh_token")
    private String refreshToken;
}
