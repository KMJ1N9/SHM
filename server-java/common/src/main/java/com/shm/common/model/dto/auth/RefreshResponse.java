package com.shm.common.model.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 刷新响应（与 Node.js refresh 返回结构完全一致）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshResponse {

    /** 新 JWT Access Token（与 Node.js camelCase 契约一致） */
    @JsonProperty("accessToken")
    private String accessToken;

    /** 新 JWT Refresh Token（与 Node.js camelCase 契约一致） */
    @JsonProperty("refreshToken")
    private String refreshToken;
}
