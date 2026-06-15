package com.shm.common.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体（对应 users 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Long id;
    private String phone;
    private String nickname;
    private String avatar;
    /** 班级（自己填） */
    private String className;
    /** 宿舍楼栋（自己填） */
    private String dormBuilding;
    private String role;
    private String status;
    /** JWT 主动失效：封禁时 +1 */
    private Integer tokenVersion;
    /** 信誉分，<60 限制发布商品 */
    private Integer creditScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
