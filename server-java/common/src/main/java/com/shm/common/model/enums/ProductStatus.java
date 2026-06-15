package com.shm.common.model.enums;

import lombok.Getter;

/**
 * 商品状态（与 MySQL ENUM('active','reserved','sold','off_shelf','deleted','frozen') 完全一致）
 */
@Getter
public enum ProductStatus {

    ACTIVE("active"),
    RESERVED("reserved"),
    SOLD("sold"),
    OFF_SHELF("off_shelf"),
    DELETED("deleted"),
    FROZEN("frozen");

    private final String value;

    ProductStatus(String value) {
        this.value = value;
    }

    public static ProductStatus fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("ProductStatus value must not be null");
        }
        for (ProductStatus s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown product status: " + value);
    }
}
