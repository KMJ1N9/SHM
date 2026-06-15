package com.shm.common.model.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信手机号登录请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /** 微信 getPhoneNumber 返回的 code */
    @NotBlank(message = "code 不能为空")
    private String code;
}
