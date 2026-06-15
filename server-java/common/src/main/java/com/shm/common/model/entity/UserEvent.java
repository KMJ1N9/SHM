package com.shm.common.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户行为事件实体（对应 user_events 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent {

    private Long id;
    private Long userId;
    /** 事件类型：view_product | search | click_want | create_order | complete_order | publish_product */
    private String event;
    /** JSON，扩展数据 */
    private String metadata;
    private LocalDateTime createdAt;
}
