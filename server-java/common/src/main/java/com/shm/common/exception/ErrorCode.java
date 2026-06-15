package com.shm.common.exception;

import lombok.Getter;

/**
 * 统一错误码枚举（与 Node.js 后端 30 个错误码完全一致）
 * <p>
 * 分段规则：0 成功；1xxx 认证与授权；2xxx 资源；3xxx 业务状态冲突；
 * 4xxx 输入与风控；5xxx 权限；6xxx 系统与第三方
 *
 * @see <a href="docs/API接口文档.md">API 接口文档 §4 错误码</a>
 */
@Getter
public enum ErrorCode {

    // ===== 成功 =====
    SUCCESS(0, "ok"),

    // ===== 1xxx 认证与授权 =====
    UNAUTHORIZED(1001, "未登录或登录已过期"),
    TOKEN_EXPIRED(1002, "Token 已失效，请重新登录"),
    TOKEN_VERSION_MISMATCH(1003, "账号已在其他设备登录，请重新登录"),
    ACCOUNT_BANNED(1004, "账号已被限制使用"),

    // ===== 2xxx 资源 =====
    NOT_FOUND(2001, "资源不存在"),
    PRODUCT_NOT_FOUND(2002, "商品不存在或已下架"),
    ORDER_NOT_FOUND(2003, "订单不存在"),
    USER_NOT_FOUND(2004, "用户不存在"),
    REVIEW_NOT_FOUND(2005, "评价不存在"),
    REPORT_NOT_FOUND(2006, "举报不存在"),

    // ===== 3xxx 业务状态冲突 =====
    ALREADY_ORDERED(3001, "您已对该商品发起过交易"),
    PRODUCT_NOT_ACTIVE(3002, "商品不可交易（已卖出/已下架）"),
    ORDER_STATUS_INVALID(3003, "当前订单状态不允许此操作"),
    CANNOT_REVIEW_OWN(3004, "不能评价自己"),
    ALREADY_REVIEWED(3005, "该订单已评价"),
    ORDER_NOT_MET(3006, "订单尚未面交，不能确认收货"),
    CANNOT_BUY_OWN(3007, "不能购买自己发布的商品"),
    DUPLICATE_REPORT(3008, "你已提交过对该用户/商品/订单的举报，请等待处理"),

    // ===== 4xxx 输入与风控 =====
    VALIDATION_ERROR(4001, "参数校验失败"),
    RATE_LIMITED(4002, "请求过于频繁，请稍后再试"),
    SENSITIVE_WORD(4003, "内容包含敏感词，请修改后重新提交"),
    FILE_TOO_LARGE(4004, "文件大小超出限制"),
    CREDIT_TOO_LOW(4005, "信誉分不足"),
    CREDIT_TOO_LOW_PUBLISH(4008, "信誉分不足，无法发布商品"),
    CREDIT_TOO_LOW_TRADE(4009, "信誉分不足，无法参与交易"),
    TOO_MANY_IMAGES(4006, "图片数量超过限制"),

    // ===== 5xxx 权限 =====
    FORBIDDEN(5001, "无权限执行此操作"),
    ROLE_REQUIRED(5002, "角色权限不足"),
    NOT_OWNER(5003, "只能操作自己的资源"),

    // ===== 6xxx 系统与第三方 =====
    INTERNAL_ERROR(6001, "服务内部错误"),
    WECHAT_API_FAILED(6003, "微信 API 调用失败"),
    IM_API_FAILED(6004, "IM 服务调用失败"),
    COS_API_FAILED(6005, "对象存储服务异常"),
    DATABASE_ERROR(6006, "数据库操作失败"),
    SERVICE_UNAVAILABLE(6999, "服务暂不可用");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 根据 code 查找错误码，找不到返回 INTERNAL_ERROR
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) {
                return ec;
            }
        }
        return INTERNAL_ERROR;
    }
}
