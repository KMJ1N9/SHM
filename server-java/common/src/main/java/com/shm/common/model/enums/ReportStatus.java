package com.shm.common.model.enums;

import lombok.Getter;

/**
 * 举报状态（与 MySQL ENUM('pending','processing','resolved') 完全一致）
 */
@Getter
public enum ReportStatus {

    PENDING("pending"),
    PROCESSING("processing"),
    RESOLVED("resolved");

    private final String value;

    ReportStatus(String value) {
        this.value = value;
    }

    public static ReportStatus fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("ReportStatus value must not be null");
        }
        for (ReportStatus s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown report status: " + value);
    }
}
