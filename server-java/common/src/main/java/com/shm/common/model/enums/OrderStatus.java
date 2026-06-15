package com.shm.common.model.enums;

import lombok.Getter;

/**
 * 订单状态（与 MySQL ENUM('pending','met','completed','cancelled','disputed','timeout') 完全一致）
 */
@Getter
public enum OrderStatus {

    PENDING("pending"),
    MET("met"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    DISPUTED("disputed"),
    TIMEOUT("timeout");

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    public static OrderStatus fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("OrderStatus value must not be null");
        }
        for (OrderStatus s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown order status: " + value);
    }
}
