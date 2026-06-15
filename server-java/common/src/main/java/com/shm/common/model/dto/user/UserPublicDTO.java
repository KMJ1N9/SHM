package com.shm.common.model.dto.user;

import com.shm.common.model.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户公开信息（与 Node.js PUBLIC_USER_FIELDS 一致）
 *
 * <p>字段：id, nickname, avatar, class_name, dorm_building, credit_score, created_at
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPublicDTO {

    private Long id;
    private String nickname;
    private String avatar;
    private String className;
    private String dormBuilding;
    private Integer creditScore;
    private LocalDateTime createdAt;

    /**
     * 从 User entity 创建公开信息视图
     */
    public static UserPublicDTO from(User user) {
        return UserPublicDTO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .className(user.getClassName())
                .dormBuilding(user.getDormBuilding())
                .creditScore(user.getCreditScore())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
