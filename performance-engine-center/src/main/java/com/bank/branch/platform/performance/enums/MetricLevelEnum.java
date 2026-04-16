package com.bank.branch.platform.performance.enums;

/**
 * 指标层级枚举.
 * <p>L1 一级指标: SQL/PROC 直接查库.
 * <p>L2 二级指标: EXPR 表达式, 只能引用 L1.
 * <p>L3 三级指标: EXPR 表达式, 只能引用 L2.
 */
public enum MetricLevelEnum {
    L1(1),
    L2(2),
    L3(3);

    private final int level;

    MetricLevelEnum(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    /** 根据数字值返回对应的枚举, 无效值抛 IllegalArgumentException. */
    public static MetricLevelEnum of(int level) {
        for (MetricLevelEnum e : values()) {
            if (e.level == level) {
                return e;
            }
        }
        throw new IllegalArgumentException("invalid metric level: " + level);
    }

    /** 判断整数是否为合法指标层级. */
    public static boolean isValid(Integer level) {
        if (level == null) {
            return false;
        }
        for (MetricLevelEnum e : values()) {
            if (e.level == level) {
                return true;
            }
        }
        return false;
    }
}
