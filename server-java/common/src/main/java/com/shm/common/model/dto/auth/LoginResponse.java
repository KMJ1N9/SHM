package com.shm.common.model.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应（与 Node.js login 返回结构完全一致）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** JWT Access Token（与 Node.js camelCase 契约一致） */
    @JsonProperty("accessToken")
    private String accessToken;

    /** JWT Refresh Token（与 Node.js camelCase 契约一致） */
    @JsonProperty("refreshToken")
    private String refreshToken;

    /** 是否新注册用户（重命名为 newUser 避免 Lombok isNewUser() getter 与 @JsonProperty 冲突产生双序列化） */
    @JsonProperty("isNewUser")
    private boolean newUser;

    /** 用户信息 */
    private UserInfo user;
}
