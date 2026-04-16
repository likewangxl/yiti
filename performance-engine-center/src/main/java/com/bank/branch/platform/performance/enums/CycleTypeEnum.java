package com.bank.branch.platform.performance.enums;

/**
 * 考核周期类型.
 */
public enum CycleTypeEnum {
    /** 月度. */
    MONTHLY,
    /** 季度. */
    QUARTERLY,
    /** 年度. */
    YEARLY;

    public static boolean isValid(String v) {
        if (v == null) {
            return false;
        }
        for (CycleTypeEnum e : values()) {
            if (e.name().equals(v)) {
                return true;
            }
        }
        return false;
    }
}
