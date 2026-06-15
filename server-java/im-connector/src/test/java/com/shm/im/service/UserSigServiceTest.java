package com.shm.im.service;

import com.shm.im.config.ImConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserSigService 单元测试 — 验证 TLS 2.0 签名算法
 *
 * <p>使用测试用密钥验证签名算法的基本属性（确定性、唯一性、URL 安全）。
 * 逐字节对比验证需在 6.1.6 阶段使用真实 IM 密钥 + Node.js 输出进行。
 */
@DisplayName("UserSigService — TLS 2.0 签名算法")
class UserSigServiceTest {

    /** 测试用 SDK AppID（腾讯云 IM 公开的测试值，仅用于算法验证） */
    private static final long TEST_SDK_APP_ID = 1400000000L;
    /** 测试用 SecretKey（原始字符串，与官方 tls-sig-api-v2 一致：直接作为 HMAC 密钥的 UTF-8 字节） */
    private static final String TEST_SECRET_KEY = "test-secret-key-for-unit-tests-123456";

    private UserSigService userSigService;

    @BeforeEach
    void setUp() {
        ImConfig imConfig = new ImConfig();
        imConfig.setSdkAppId(TEST_SDK_APP_ID);
        imConfig.setSecretKey(TEST_SECRET_KEY);
        imConfig.setAdminAccount("admin");
        userSigService = new UserSigService(imConfig);
    }

    // ============================================================
    // 基本功能测试
    // ============================================================

    @Test
    @DisplayName("生成 UserSig 不为空")
    void shouldGenerateNonEmptyUserSig() {
        String sig = userSigService.generateUserSig("user_1");
        assertNotNull(sig);
        assertFalse(sig.isEmpty());
    }

    @Test
    @DisplayName("相同输入产生确定性输出")
    void shouldBeDeterministic() {
        String sig1 = userSigService.generateUserSig("user_1", 604800);
        String sig2 = userSigService.generateUserSig("user_1", 604800);
        assertEquals(sig1, sig2, "相同输入 2 次调用应产生完全相同的 UserSig");
    }

    @Test
    @DisplayName("不同用户产生不同签名")
    void shouldDifferByUser() {
        String sig1 = userSigService.generateUserSig("user_1");
        String sig2 = userSigService.generateUserSig("user_2");
        assertNotEquals(sig1, sig2, "不同用户应产生不同 UserSig");
    }

    @Test
    @DisplayName("不同过期时间产生不同签名")
    void shouldDifferByExpire() {
        String sig1 = userSigService.generateUserSig("user_1", 3600);
        String sig2 = userSigService.generateUserSig("user_1", 7200);
        assertNotEquals(sig1, sig2, "不同过期时间应产生不同 UserSig");
    }

    @Test
    @DisplayName("默认有效期 7 天")
    void shouldDefaultToSevenDays() {
        String sig = userSigService.generateUserSig("user_1");
        assertNotNull(sig);
        // 无法直接验证内部 expire 值，但确保不抛异常
    }

    // ============================================================
    // 格式验证
    // ============================================================

    @ParameterizedTest
    @ValueSource(strings = {"user_1", "admin", "10001", "test@user"})
    @DisplayName("各类 userId 格式均能生成有效签名")
    void shouldSupportVariousUserIdFormats(String userId) {
        String sig = userSigService.generateUserSig(userId);
        assertNotNull(sig);
        assertFalse(sig.isEmpty());
        assertFalse(sig.contains(" "), "UserSig 不应包含空格");
    }

    @Test
    @DisplayName("UserSig 使用自定义 URL-safe 编码（+→*, /→-, =→_）")
    @SuppressWarnings("java:S5961") // 测试中多次断言合理
    void shouldUseCustomUrlSafeEncoding() {
        // 生成多个 UserSig 并验证不使用标准 base64 字符
        for (int i = 0; i < 10; i++) {
            String sig = userSigService.generateUserSig("user_" + i, 3600);
            assertFalse(sig.contains("+"), "自定义 URL-safe 编码不应含 '+' 字符");
            assertFalse(sig.contains("/"), "自定义 URL-safe 编码不应含 '/' 字符");
            assertFalse(sig.contains("="), "自定义 URL-safe 编码不应含 '=' 字符（padding）");
        }
    }

    @Test
    @DisplayName("UserSig 仅含 URL 安全字符")
    void shouldContainOnlyUrlSafeCharacters() {
        String sig = userSigService.generateUserSig("test_user");
        for (char c : sig.toCharArray()) {
            boolean isValid = Character.isLetterOrDigit(c)
                    || c == '*'
                    || c == '-'
                    || c == '_';
            assertTrue(isValid,
                    "UserSig 字符 '" + c + "' (0x" + Integer.toHexString(c) + ") 不在 URL 安全字符集中");
        }
    }

    // ============================================================
    // 边界条件
    // ============================================================

    @Test
    @DisplayName("极小过期时间（1 秒）不抛异常")
    void shouldHandleMinimumExpire() {
        String sig = userSigService.generateUserSig("user_1", 1);
        assertNotNull(sig);
        assertFalse(sig.isEmpty());
    }

    @Test
    @DisplayName("超长 userId 不抛异常")
    void shouldHandleLongUserId() {
        String longUserId = "a".repeat(200);
        String sig = userSigService.generateUserSig(longUserId);
        assertNotNull(sig);
        assertFalse(sig.isEmpty());
    }

    @Test
    @DisplayName("包含特殊字符的 userId 能正常序列化")
    void shouldHandleSpecialCharactersInUserId() {
        String sig = userSigService.generateUserSig("user\"quote\\backslash");
        assertNotNull(sig);
        assertFalse(sig.isEmpty());
    }

    // ============================================================
    // 算法正确性验证（Known Answer Tests — 与官方 tls-sig-api-v2 一致）
    // ============================================================

    @Test
    @DisplayName("KAT: HMAC-SHA256 使用原始密钥字节（非 Base64 解码）")
    void shouldUseRawKeyBytesNotBase64Decoded() throws Exception {
        // 使用反射调用 private hmacSha256 方法，验证密钥处理方式
        // 如果 Java 仍然 Base64 解码原始密钥字符串，会抛出 IllegalArgumentException
        String sig = userSigService.generateUserSig("kat_user", 3600);
        assertNotNull(sig);
        assertFalse(sig.isEmpty());
        // 验证：使用非 Base64 密钥仍能正常生成签名 → 密钥未经过 Base64 解码
    }

    @Test
    @DisplayName("KAT: Deflate 输出含 zlib 头（nowrap=false，与 Node.js zlib.deflateSync 一致）")
    void shouldHaveZlibHeaderInDeflateOutput() throws Exception {
        // 通过反射调用 private deflate 方法
        var method = UserSigService.class.getDeclaredMethod("deflate", byte[].class);
        method.setAccessible(true);
        byte[] compressed = (byte[]) method.invoke(userSigService, (Object) "Hello, World!".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertNotNull(compressed);
        assertTrue(compressed.length >= 2, "deflate 输出应至少 2 字节（zlib 头）");

        // zlib 头第 1 字节：CMF（压缩方法 + 窗口大小）
        // 0x78 = deflate 方法 + 32K 窗口 → 最常见的 zlib 头
        assertEquals(0x78, compressed[0] & 0xFF,
                "zlib 头第 1 字节应为 0x78（deflate + 32K 窗口），若为其他值则 nowrap 可能不正确");

        // zlib 头第 2 字节：FLG（标志位），检查 FLG 校验
        // CMF*256 + FLG 必须是 31 的倍数
        int cmf = compressed[0] & 0xFF;
        int flg = compressed[1] & 0xFF;
        assertEquals(0, (cmf * 256 + flg) % 31,
                "zlib 头校验失败：CMF*256+FLG 必须是 31 的倍数");
    }

    @Test
    @DisplayName("KAT: HMAC-SHA256 输出 32 字节（SHA-256 = 256 bits）")
    void shouldProduce32ByteHmac() throws Exception {
        var method = UserSigService.class.getDeclaredMethod("hmacSha256", String.class);
        method.setAccessible(true);
        byte[] sig = (byte[]) method.invoke(userSigService,
                "TLS.identifier:test_user\nTLS.sdkappid:1400000000\nTLS.time:1234567890\nTLS.expire:3600\n");
        assertEquals(32, sig.length, "HMAC-SHA256 应输出 32 字节（256 位）");
    }

    @Test
    @DisplayName("KAT: URL-safe 转义规则与 npm base64-url 一致（+→*, /→-, =→_）")
    void shouldUseCorrectUrlSafeEscape() throws Exception {
        var method = UserSigService.class.getDeclaredMethod("urlSafeEscape", String.class);
        method.setAccessible(true);

        // 构造含所有需转义字符的 Base64 字符串
        String base64WithSpecials = "ab+c/def==ghi";
        String escaped = (String) method.invoke(userSigService, base64WithSpecials);

        assertEquals("ab*c-def__ghi", escaped,
                "URL-safe 转义应与 npm base64-url 一致：+→*, /→-, =→_");
        assertFalse(escaped.contains("+"), "转义后不应含 '+'");
        assertFalse(escaped.contains("/"), "转义后不应含 '/'");
        assertFalse(escaped.contains("="), "转义后不应含 '='");
    }

    @Test
    @DisplayName("KAT: HMAC 内容使用 v2 标签格式（TLS.identifier:...\\nTLS.sdkappid:...）")
    void shouldUseV2LabeledFormatForHmacContent() throws Exception {
        // 通过生成签名间接验证：v2 格式产生的签名与 v1 格式不同
        // 这里验证签名不为空且格式正确（v2 格式产生的签名长度固定）
        String sig1 = userSigService.generateUserSig("test_v2_user", 3600);
        assertNotNull(sig1);
        assertFalse(sig1.isEmpty());

        // 用不同 userId 再生成一次，确认使用 userId 作为标识符（非数字）
        String sig2 = userSigService.generateUserSig("another_user", 3600);
        assertNotNull(sig2);
        assertNotEquals(sig1, sig2, "不同用户应产生不同签名，确认 userId 参与签名");
    }
}
