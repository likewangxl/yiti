package com.bank.branch.platform.performance.enums;

/**
 * 指标基础维度.
 * <p>EMP/ORG/CUST 三种维度分别对应员工、机构、客户.
 */
public enum BaseDimEnum {
    /** 员工维度. */
    EMP,
    /** 机构维度. */
    ORG,
    /** 客户维度. */
    CUST;

    /** 判断字符串是否为合法维度值. */
    public static boolean isValid(String v) {
        if (v == null) {
            return false;
        }
        for (BaseDimEnum e : values()) {
            if (e.name().equals(v)) {
                return true;
            }
        }
        return false;
    }
}
