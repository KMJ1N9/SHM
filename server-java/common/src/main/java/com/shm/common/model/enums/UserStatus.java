package com.shm.common.model.enums;

import lombok.Getter;

/**
 * 用户状态（与 MySQL ENUM('active','banned') 完全一致）
 */
@Getter
public enum UserStatus {

    ACTIVE("active"),
    BANNED("banned");

    private final String value;

    UserStatus(String value) {
        this.value = value;
    }

    public static UserStatus fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("UserStatus value must not be null");
        }
        for (UserStatus s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + value);
    }
}
