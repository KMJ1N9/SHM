package com.shm.common.util;

/**
 * JWT Token 解析后的载荷（与 Node.js JWT payload 字段完全一致）
 *
 * <p>Access Token payload: { sub, role, tv }
 * <p>Refresh Token payload: { sub, tv, type: "refresh" }
 */
public class JwtPayload {

    /** 用户 ID（对应 JWT sub 字段） */
    private final Long sub;
    /** 用户角色（仅 Access Token 包含） */
    private final String role;
    /** Token 版本号，封禁用户时 +1 使所有旧 Token 失效 */
    private final Integer tv;
    /** Token 类型：null=access, "refresh"=refresh（仅 Refresh Token 包含） */
    private final String type;

    public JwtPayload(Long sub, String role, Integer tv, String type) {
        this.sub = sub;
        this.role = role;
        this.tv = tv;
        this.type = type;
    }

    public Long getSub() { return sub; }
    public String getRole() { return role; }
    public Integer getTv() { return tv; }
    public String getType() { return type; }

    /** 是否为 Refresh Token */
    public boolean isRefreshToken() {
        return "refresh".equals(type);
    }

    @Override
    public String toString() {
        return "JwtPayload{sub=" + sub + ", role=" + role + ", tv=" + tv + ", type=" + type + "}";
    }
}
