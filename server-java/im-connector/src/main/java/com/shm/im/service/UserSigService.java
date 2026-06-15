package com.shm.im.service;

import com.shm.im.config.ImConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Deflater;

/**
 * 腾讯云 IM UserSig 生成服务（TLS 2.0 标准）
 *
 * <p>算法与腾讯云官方 tls-sig-api-v2（Java 版）逐字节一致，
 * 从而与 Node.js tls-sig-api-v2 库的 genUserSig 输出<b>逐字节一致</b>。
 *
 * <h3>TLS 2.0 签名流程（v2 格式，与官方库一致）</h3>
 * <ol>
 *   <li>签名内容（带字段标签）："TLS.identifier:{id}\nTLS.sdkappid:{appid}\nTLS.time:{time}\nTLS.expire:{expire}\n"</li>
 *   <li>HMAC-SHA256 签名 — 密钥使用 IM SecretKey 的 UTF-8 原始字节（<b>不</b>做 Base64 解码）</li>
 *   <li>构建 sigDoc JSON: {"TLS.ver":"2.0","TLS.identifier":"...","TLS.sdkappid":...,"TLS.expire":...,"TLS.time":...,"TLS.sig":"..."}</li>
 *   <li>zlib deflate 压缩 JSON（含 zlib 头，与 Node.js zlib.deflateSync 一致）</li>
 *   <li>Base64 编码 + 自定义 URL-safe 转义（+→*, /→-, =→_，与 npm base64-url 包一致）</li>
 * </ol>
 *
 * @see <a href="https://github.com/tencentyun/tls-sig-api-v2-java">官方 Java 实现</a>
 * @see <a href="https://cloud.tencent.com/document/product/269/32688">UserSig 生成文档</a>
 */
@Service
public class UserSigService {

    private static final Logger log = LoggerFactory.getLogger(UserSigService.class);

    /** UserSig 默认有效期（秒）：7 天 */
    private static final int DEFAULT_EXPIRE_SECONDS = 604800;

    private final ImConfig imConfig;

    public UserSigService(ImConfig imConfig) {
        this.imConfig = imConfig;
    }

    /**
     * 为用户生成 UserSig（默认 7 天有效期）
     *
     * @param userId 用户 ID（将转为字符串作为 IM Identifier）
     * @return URL-safe 的 UserSig 字符串
     */
    public String generateUserSig(String userId) {
        return generateUserSig(userId, DEFAULT_EXPIRE_SECONDS);
    }

    /**
     * 为用户生成 UserSig（指定有效期）
     *
     * @param userId       用户 ID
     * @param expireSeconds 有效期（秒）
     * @return URL-safe 的 UserSig 字符串
     */
    public String generateUserSig(String userId, int expireSeconds) {
        long sdkAppId = imConfig.getSdkAppId();
        long now = System.currentTimeMillis() / 1000;

        // 1. HMAC-SHA256 签名 — 使用 v2 标签格式（与官方 tls-sig-api-v2 一致）
        //    格式：TLS.identifier:{id}\nTLS.sdkappid:{appid}\nTLS.time:{time}\nTLS.expire:{expire}\n
        String contentToSign = "TLS.identifier:" + userId + "\n"
                + "TLS.sdkappid:" + sdkAppId + "\n"
                + "TLS.time:" + now + "\n"
                + "TLS.expire:" + expireSeconds + "\n";

        byte[] sigBytes = hmacSha256(contentToSign);
        String sigBase64 = Base64.getEncoder().encodeToString(sigBytes);

        // 2. 构建 sigDoc JSON
        String sigDoc = "{\"TLS.ver\":\"2.0\","
                + "\"TLS.identifier\":\"" + escapeJson(userId) + "\","
                + "\"TLS.sdkappid\":" + sdkAppId + ","
                + "\"TLS.expire\":" + expireSeconds + ","
                + "\"TLS.time\":" + now + ","
                + "\"TLS.sig\":\"" + escapeJson(sigBase64) + "\"}";

        log.debug("UserSig sigDoc content: {} chars", sigDoc.length());

        // 3. zlib deflate 压缩（含 zlib 头，与 Node.js zlib.deflateSync 一致）
        byte[] compressed = deflate(sigDoc.getBytes(StandardCharsets.UTF_8));

        // 4. Base64 编码 + 自定义 URL-safe 转义（与 npm base64-url 包一致：+→*, /→-, =→_）
        String base64 = Base64.getEncoder().encodeToString(compressed);
        return urlSafeEscape(base64);
    }

    // ============================================================
    // 内部方法
    // ============================================================

    /** HMAC-SHA256 签名 — 密钥使用 IM SecretKey 的 UTF-8 原始字节（与官方 TLS v2 一致，不做 Base64 解码） */
    private byte[] hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(getKeyBytes(), "HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 签名失败", e);
        }
    }

    /** 获取 IM SecretKey 的 UTF-8 字节（延迟校验 + 缓存） */
    private byte[] getKeyBytes() {
        String secretKey = imConfig.getSecretKey();
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("IM_SECRET_KEY 未配置，请在 .env 或环境变量中设置");
        }
        return secretKey.getBytes(StandardCharsets.UTF_8);
    }

    /** zlib deflate 压缩（含 zlib 头，与 Node.js zlib.deflateSync / 官方 Java 实现一致） */
    private byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION); // nowrap=false → 含 zlib 头
        deflater.setInput(input);
        deflater.finish();
        byte[] buf = new byte[4096];
        int len = deflater.deflate(buf);
        deflater.end();
        byte[] result = new byte[len];
        System.arraycopy(buf, 0, result, 0, len);
        return result;
    }

    /** JSON 字符串转义（仅处理双引号和反斜杠） */
    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 自定义 URL-safe 转义（与 npm base64-url 包 escape 一致）
     *
     * <p>npm base64-url escape: + → *, / → -, = → _
     * <p>注意：这与 RFC 4648 base64url（+ → -, / → _）不同！
     */
    private String urlSafeEscape(String base64) {
        return base64
                .replace('+', '*')
                .replace('/', '-')
                .replace('=', '_');
    }
}
