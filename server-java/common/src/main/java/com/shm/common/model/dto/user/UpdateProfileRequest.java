package com.shm.common.model.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 编辑个人资料请求（与 Node.js userService.updateProfile 的 allowed 字段一致）
 *
 * <p>所有字段可选，只更新传入的非 null 字段。
 * <p>前端发送 snake_case JSON，JacksonConfig 反序列化默认驼峰，需显式映射。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    private String nickname;
    private String avatar;

    @JsonProperty("class_name")
    private String className;

    @JsonProperty("dorm_building")
    private String dormBuilding;
}
