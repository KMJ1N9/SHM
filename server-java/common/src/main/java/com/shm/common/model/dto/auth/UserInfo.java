package com.shm.common.model.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息视图（与 Node.js login/me 返回的 user 对象结构完全一致）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {

    private Long id;
    /** 手机号（login 返回完整手机号，后续接口脱敏为 138****3800） */
    private String phone;
    private String nickname;
    private String avatar;
    /** 班级（Jackson SNAKE_CASE → class_name） */
    private String className;
    /** 宿舍楼栋（Jackson SNAKE_CASE → dorm_building） */
    private String dormBuilding;
    private String role;
    /** 信誉分（Jackson SNAKE_CASE → credit_score） */
    private Integer creditScore;

    /**
     * 从 User entity 创建 UserInfo（只暴露前端需要的字段）
     */
    public static UserInfo from(com.shm.common.model.entity.User user) {
        return UserInfo.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .className(user.getClassName())
                .dormBuilding(user.getDormBuilding())
                .role(user.getRole())
                .creditScore(user.getCreditScore())
                .build();
    }
}
