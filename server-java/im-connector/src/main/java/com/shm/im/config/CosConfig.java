package com.shm.im.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 腾讯云 COS 配置（与 Node.js config/cos 一致）
 *
 * <p>配置项从 application.yml 的 tencent.cos.* 读取，
 * 支持环境变量覆盖（COS_BUCKET / COS_REGION / COS_SECRET_ID / COS_SECRET_KEY）。
 */
@ConfigurationProperties(prefix = "tencent.cos")
public class CosConfig {

    /** COS Bucket 名称 */
    private String bucket;

    /** COS 区域代码 */
    private String region = "ap-guangzhou";

    /** COS SecretId */
    private String secretId;

    /** COS SecretKey */
    private String secretKey;

    /** CDN 加速域名（可选，默认使用 COS 源站域名） */
    private String cdnBaseUrl;

    /** 是否强制使用 mock 模式（跳过 COS 直传，走服务端上传） */
    private boolean mock = false;

    /** 上传大小限制（字节），默认 5MB */
    private long uploadMaxSize = 5_242_880L;

    /** 临时密钥有效期（秒），默认 30 分钟 */
    private int durationSeconds = 1800;

    // ---- getters / setters ----

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getSecretId() { return secretId; }
    public void setSecretId(String secretId) { this.secretId = secretId; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getCdnBaseUrl() { return cdnBaseUrl; }
    public void setCdnBaseUrl(String cdnBaseUrl) { this.cdnBaseUrl = cdnBaseUrl; }

    public boolean isMock() { return mock; }
    public void setMock(boolean mock) { this.mock = mock; }

    public long getUploadMaxSize() { return uploadMaxSize; }
    public void setUploadMaxSize(long uploadMaxSize) { this.uploadMaxSize = uploadMaxSize; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
}
