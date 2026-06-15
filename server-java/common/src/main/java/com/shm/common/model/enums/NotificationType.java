package com.shm.common.model.enums;

import lombok.Getter;

/**
 * 通知类型（与 MySQL VARCHAR(50) 对应，通知中心 4 种类型）
 */
@Getter
public enum NotificationType {

    ORDER_UPDATE("order_update"),
    REVIEW_REMIND("review_remind"),
    REPORT_RESULT("report_result"),
    CREDIT_CHANGE("credit_change");

    private final String value;

    NotificationType(String value) {
        this.value = value;
    }

    public static NotificationType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("NotificationType value must not be null");
        }
        for (NotificationType t : values()) {
            if (t.value.equals(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown notification type: " + value);
    }
}
