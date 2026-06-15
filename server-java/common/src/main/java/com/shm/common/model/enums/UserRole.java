package com.shm.common.model.enums;

import lombok.Getter;

/**
 * 用户角色（与 MySQL ENUM('user','cs','admin') 完全一致）
 */
@Getter
public enum UserRole {

    USER("user"),
    CS("cs"),
    ADMIN("admin");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    public static UserRole fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("UserRole value must not be null");
        }
        for (UserRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}
