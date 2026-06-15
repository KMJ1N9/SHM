package com.shm.common.model.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客服/管理员联系方式（与 Node.js getCSContact/getAdminContact 返回格式一致）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactDTO {

    private Long id;
    private String nickname;
    private String avatar;
}
