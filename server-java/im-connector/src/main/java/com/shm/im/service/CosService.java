package com.shm.im.service;

import com.shm.im.config.CosConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 腾讯云 COS STS 临时凭证服务（与 Node.js utils/cos.js 行为一致）
 *
 * <h3>前端直传 COS 流程</h3>
 * <ol>
 *   <li>前端调用 GET /api/upload/cos-credential → 本服务生成临时凭证</li>
 *   <li>前端拿临时凭证 + COS JS SDK 直传图片</li>
 *   <li>临时凭证 30 分钟过期，前端需提前刷新</li>
 * </ol>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>按用户 ID 隔离上传路径（user_{userId}/）</li>
 *   <li>限制 content-type 白名单（仅 image/）</li>
 *   <li>限制单文件大小</li>
 * </ul>
 *
 * <p>当前 MVP 阶段使用 HMAC-SHA1 签名方案作为简化版 STS。
 * 生产环境应接入 STS SDK（STSClient.getCredential），
 * 本实现的过期策略与 STS 一致（30 分钟）。
 */
@Service
public class CosService {

    private static final Logger log = LoggerFactory.getLogger(CosService.class);

    /** COS policy 过期时间格式（与 Node.js {@code Date.toISOString()} — 含毫秒 .000Z — 完全一致） */
    private static final DateTimeFormatter COS_EXPIRATION_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC);

    private final CosConfig cosConfig;
    private final Environment env;

    public CosService(CosConfig cosConfig, Environment env) {
        this.cosConfig = cosConfig;
        this.env = env;
    }

    /**
     * 生成 COS 临时上传凭证
     *
     * @param userId 用户 ID（用于隔离路径）
     * @return 包含 credentials、policy、expiredTime、prefix、bucket、region、cdnBaseUrl 的 Map
     */
    public Map<String, Object> generateCredential(Long userId) {
        String prefix = "user_" + userId + "/";
        int durationSeconds = cosConfig.getDurationSeconds();
        long now = System.currentTimeMillis() / 1000;
        long expiredTime = now + durationSeconds;

        // 检测是否需要走 mock 模式（与 Node.js cos.js 行为一致）：
        //   1. 开发环境始终走服务端上传（避免 COS policy 字段遗漏导致 403 阻断开发流程）
        //   2. 未配置真实 COS 凭证时（含 "placeholder" 占位字符串）
        //   3. SecretKey 缺失/为空
        String bucket = cosConfig.getBucket();
        String secretId = cosConfig.getSecretId();
        String secretKey = cosConfig.getSecretKey();
        boolean isDev = Arrays.asList(env.getActiveProfiles()).contains("dev");
        boolean isMock = isDev
                || cosConfig.isMock()
                || bucket == null || bucket.contains("placeholder")
                || secretId == null || secretId.contains("placeholder")
                || secretKey == null || secretKey.isBlank();

        if (isMock) {
            log.debug("COS 凭证使用 mock 模式（bucket={}, secretId={}）",
                    bucket != null ? bucket.substring(0, Math.min(8, bucket.length())) + "..." : "null",
                    secretId != null ? "***" : "null");
            return buildMockCredential(prefix, now, expiredTime);
        }

        return buildRealCredential(prefix, now, expiredTime);
    }

    // ============================================================
    // 内部方法
    // ============================================================

    /** 构建真实 COS 凭证（HMAC-SHA1 签名 policy） */
    private Map<String, Object> buildRealCredential(String prefix, long now, long expiredTime) {
        String bucket = cosConfig.getBucket();
        String region = cosConfig.getRegion();
        String secretId = cosConfig.getSecretId();
        String secretKey = cosConfig.getSecretKey();
        long uploadMaxSize = cosConfig.getUploadMaxSize();

        // 构建 policy（与 Node.js 格式完全一致，含毫秒 .000Z）
        String expiration = COS_EXPIRATION_FMT.format(Instant.ofEpochSecond(expiredTime));
        String policyJson = "{"
                + "\"expiration\":\"" + expiration + "\","
                + "\"conditions\":["
                + "{\"bucket\":\"" + bucket + "\"},"
                + "[\"starts-with\",\"$Content-Type\",\"image/\"],"
                + "[\"content-length-range\",1," + uploadMaxSize + "],"
                + "[\"starts-with\",\"$key\",\"" + prefix + "\"]"
                + "]}";

        String policyBase64 = Base64.getEncoder()
                .encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));

        // HMAC-SHA1 签名
        String signKey = hmacSha1(secretKey, policyBase64);

        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("tmpSecretId", secretId);
        credentials.put("tmpSecretKey", secretKey);
        credentials.put("sessionToken", "");
        credentials.put("signKey", signKey);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mock", false);
        result.put("credentials", credentials);
        result.put("policy", policyBase64);
        result.put("expiredTime", expiredTime);
        result.put("prefix", prefix);
        result.put("bucket", bucket);
        result.put("region", region);
        result.put("cdnBaseUrl",
                cosConfig.getCdnBaseUrl() != null && !cosConfig.getCdnBaseUrl().isEmpty()
                        ? cosConfig.getCdnBaseUrl()
                        : "https://" + bucket + ".cos." + region + ".myqcloud.com");

        return result;
    }

    /** 构建 mock 凭证（开发环境，前端跳过实际上传） */
    private Map<String, Object> buildMockCredential(String prefix, long now, long expiredTime) {
        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("tmpSecretId", "mock-secret-id");
        credentials.put("tmpSecretKey", "mock-secret-key");
        credentials.put("sessionToken", "");
        credentials.put("signKey", "mock-sign-key");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mock", true);
        result.put("credentials", credentials);
        result.put("policy", "mock-policy");
        result.put("expiredTime", expiredTime);
        result.put("prefix", prefix);
        result.put("bucket", "mock-bucket");
        result.put("region", "mock-region");
        result.put("cdnBaseUrl", "");

        return result;
    }

    /** HMAC-SHA1 签名，返回 Base64 字符串 */
    private String hmacSha1(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec keySpec = new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(keySpec);
            byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA1 签名失败", e);
        }
    }
}
