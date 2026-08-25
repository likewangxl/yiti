package com.bank.branch.platform.customer.enums;

import lombok.Getter;

import java.util.Arrays;

/** 客户标签触达规则允许的周期单位。 */
@Getter
public enum TouchLimitCycleUnit {

    DAY,
    WEEK,
    MONTH,
    QUARTER,
    YEAR;

    /** 判断字符串是否为接口契约支持的周期单位。 */
    public static boolean isSupported(String value) {
        if (value == null) {
            return false;
        }
        return Arrays.stream(values()).anyMatch(unit -> unit.name().equals(value));
    }
}
