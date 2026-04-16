package com.bank.branch.platform.performance.enums;

/**
 * 指标计算逻辑类型.
 * <p>SQL: 一级指标, 使用 sql_text 的 SELECT 语句.
 * <p>PROC: 一级指标, 使用 sql_text 的存储过程名.
 * <p>EXPR: 二/三级指标, Groovy 表达式.
 * <p>SUMMARY: 机构汇总 (对 ref_metric_codes 按 summary_rule 聚合).
 */
public enum CalcLogicTypeEnum {
    SQL,
    PROC,
    EXPR,
    SUMMARY;

    public static boolean isValid(String v) {
        if (v == null) {
            return false;
        }
        for (CalcLogicTypeEnum e : values()) {
            if (e.name().equals(v)) {
                return true;
            }
        }
        return false;
    }
}
