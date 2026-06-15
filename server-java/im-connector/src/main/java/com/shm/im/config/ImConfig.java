package com.shm.im.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 腾讯云 IM 配置（与 Node.js config/im 一致）
 *
 * <p>配置项从 application.yml 的 tencent.im.* 读取，
 * 支持环境变量覆盖（IM_SDK_APP_ID / IM_SECRET_KEY / IM_ADMIN_ACCOUNT）。
 */
@ConfigurationProperties(prefix = "tencent.im")
public class ImConfig {

    /** IM 应用 SDKAppID */
    private long sdkAppId;

    /** IM 应用密钥（HMAC-SHA256 UserSig 签名用） */
    private String secretKey;

    /** IM 管理员账号（系统消息发送者），默认 administrator */
    private String adminAccount = "administrator";

    // ---- getters / setters ----

    public long getSdkAppId() { return sdkAppId; }
    public void setSdkAppId(long sdkAppId) { this.sdkAppId = sdkAppId; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getAdminAccount() { return adminAccount; }
    public void setAdminAccount(String adminAccount) { this.adminAccount = adminAccount; }
}
