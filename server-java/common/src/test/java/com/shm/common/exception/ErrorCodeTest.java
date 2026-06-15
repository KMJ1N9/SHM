package com.shm.common.exception;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ErrorCode 枚举单元测试（Phase 11 11.1.4）
 *
 * <p>验证 30 个错误码的 fromCode 反向查找、code 唯一性、范围正确性。
 */
class ErrorCodeTest {

    // ============================================================
    // fromCode — 正向匹配
    // ============================================================

    @Test
    void fromCode_zero_shouldReturnSuccess() {
        assertEquals(ErrorCode.SUCCESS, ErrorCode.fromCode(0));
    }

    @Test
    void fromCode_authCodes_shouldReturnCorrectEnum() {
        assertEquals(ErrorCode.UNAUTHORIZED, ErrorCode.fromCode(1001));
        assertEquals(ErrorCode.TOKEN_EXPIRED, ErrorCode.fromCode(1002));
        assertEquals(ErrorCode.TOKEN_VERSION_MISMATCH, ErrorCode.fromCode(1003));
        assertEquals(ErrorCode.ACCOUNT_BANNED, ErrorCode.fromCode(1004));
    }

    @Test
    void fromCode_resourceCodes_shouldReturnCorrectEnum() {
        assertEquals(ErrorCode.NOT_FOUND, ErrorCode.fromCode(2001));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ErrorCode.fromCode(2002));
        assertEquals(ErrorCode.ORDER_NOT_FOUND, ErrorCode.fromCode(2003));
        assertEquals(ErrorCode.USER_NOT_FOUND, ErrorCode.fromCode(2004));
        assertEquals(ErrorCode.REVIEW_NOT_FOUND, ErrorCode.fromCode(2005));
        assertEquals(ErrorCode.REPORT_NOT_FOUND, ErrorCode.fromCode(2006));
    }

    @Test
    void fromCode_businessCodes_shouldReturnCorrectEnum() {
        assertEquals(ErrorCode.ALREADY_ORDERED, ErrorCode.fromCode(3001));
        assertEquals(ErrorCode.PRODUCT_NOT_ACTIVE, ErrorCode.fromCode(3002));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, ErrorCode.fromCode(3003));
        assertEquals(ErrorCode.CANNOT_REVIEW_OWN, ErrorCode.fromCode(3004));
        assertEquals(ErrorCode.ALREADY_REVIEWED, ErrorCode.fromCode(3005));
        assertEquals(ErrorCode.ORDER_NOT_MET, ErrorCode.fromCode(3006));
        assertEquals(ErrorCode.CANNOT_BUY_OWN, ErrorCode.fromCode(3007));
    }

    @Test
    void fromCode_inputCodes_shouldReturnCorrectEnum() {
        assertEquals(ErrorCode.VALIDATION_ERROR, ErrorCode.fromCode(4001));
        assertEquals(ErrorCode.RATE_LIMITED, ErrorCode.fromCode(4002));
        assertEquals(ErrorCode.SENSITIVE_WORD, ErrorCode.fromCode(4003));
        assertEquals(ErrorCode.FILE_TOO_LARGE, ErrorCode.fromCode(4004));
        assertEquals(ErrorCode.CREDIT_TOO_LOW, ErrorCode.fromCode(4005));
        assertEquals(ErrorCode.TOO_MANY_IMAGES, ErrorCode.fromCode(4006));
        assertEquals(ErrorCode.CREDIT_TOO_LOW_PUBLISH, ErrorCode.fromCode(4008));
        assertEquals(ErrorCode.CREDIT_TOO_LOW_TRADE, ErrorCode.fromCode(4009));
    }

    @Test
    void fromCode_permissionCodes_shouldReturnCorrectEnum() {
        assertEquals(ErrorCode.FORBIDDEN, ErrorCode.fromCode(5001));
        assertEquals(ErrorCode.ROLE_REQUIRED, ErrorCode.fromCode(5002));
        assertEquals(ErrorCode.NOT_OWNER, ErrorCode.fromCode(5003));
    }

    @Test
    void fromCode_systemCodes_shouldReturnCorrectEnum() {
        assertEquals(ErrorCode.INTERNAL_ERROR, ErrorCode.fromCode(6001));
        assertEquals(ErrorCode.WECHAT_API_FAILED, ErrorCode.fromCode(6003));
        assertEquals(ErrorCode.IM_API_FAILED, ErrorCode.fromCode(6004));
        assertEquals(ErrorCode.COS_API_FAILED, ErrorCode.fromCode(6005));
        assertEquals(ErrorCode.DATABASE_ERROR, ErrorCode.fromCode(6006));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ErrorCode.fromCode(6999));
    }

    @Test
    void fromCode_unknownCode_shouldFallbackToInternalError() {
        assertEquals(ErrorCode.INTERNAL_ERROR, ErrorCode.fromCode(9999));
        assertEquals(ErrorCode.INTERNAL_ERROR, ErrorCode.fromCode(-1));
        assertEquals(ErrorCode.INTERNAL_ERROR, ErrorCode.fromCode(7000));
    }

    // ============================================================
    // 唯一性
    // ============================================================

    @Test
    void allCodes_shouldBeUnique() {
        ErrorCode[] values = ErrorCode.values();
        Set<Integer> codeSet = new HashSet<>();
        for (ErrorCode ec : values) {
            assertTrue(codeSet.add(ec.getCode()),
                    "Duplicate code found: " + ec.getCode());
        }
    }

    // ============================================================
    // 枚举总数
    // ============================================================

    @Test
    void values_shouldHaveExpectedCount() {
        // 枚举总数 = 35（1 成功 + 4 认证 + 6 资源 + 7 业务 + 8 输入 + 3 权限 + 6 系统）
        assertEquals(35, ErrorCode.values().length,
                "枚举总数应与 Node.js 后端错误码完全一致");
    }

    // ============================================================
    // SUCCESS
    // ============================================================

    @Test
    void success_codeShouldBeZero() {
        assertEquals(0, ErrorCode.SUCCESS.getCode());
    }

    @Test
    void success_messageShouldBeOk() {
        assertEquals("ok", ErrorCode.SUCCESS.getMessage());
    }

    // ============================================================
    // 范围正确性
    // ============================================================

    @Test
    void codeRange_shouldMatchCategory() {
        for (ErrorCode ec : ErrorCode.values()) {
            int code = ec.getCode();
            int category = code / 1000;

            String name = ec.name();
            if (code == 0) {
                assertEquals(0, category, name + " should be success category");
            } else {
                assertTrue(category >= 1 && category <= 6,
                        name + " has code " + code + " with unexpected category " + category);
            }
        }
    }

    // ============================================================
    // message 非空
    // ============================================================

    @Test
    void allMessages_shouldBeNonNullAndNonEmpty() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertNotNull(ec.getMessage(), "Message is null for " + ec.name());
            assertFalse(ec.getMessage().isEmpty(), "Message is empty for " + ec.name());
        }
    }

    // ============================================================
    // fromCode 性能 — 所有未知 code 均降级不抛异常
    // ============================================================

    @Test
    void fromCode_unknownCodesShouldNotThrow() {
        for (int code = 1; code <= 1000; code++) {
            final int c = code;
            assertDoesNotThrow(() -> ErrorCode.fromCode(c),
                    "fromCode(" + c + ") should not throw");
        }
    }
}
