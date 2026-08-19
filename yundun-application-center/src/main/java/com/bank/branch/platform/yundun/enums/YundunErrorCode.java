package com.bank.branch.platform.yundun.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 浦爱云盾模块错误码。 */
@Getter
@AllArgsConstructor
public enum YundunErrorCode {
    IMPORT_INVALID("YD-40001", "导入文件无有效数据"),
    EXPORT_LIMIT("YD-40002", "导出数据超过 5000 条，请缩小查询范围"),
    IMPORT_LIMIT("YD-40003", "单次最多导入 3000 条数据"),
    IMPORT_PARSE_FAILED("YD-40004", "Excel 解析失败，请检查模板和字段格式"),
    IMPORT_FILE_TOO_LARGE("YD-40005", "导入文件不能超过 10MB"),
    ACCOUNTABILITY_NOT_FOUND("YD-40401", "人员违规记录不存在"),
    CREDIT_NOT_FOUND("YD-40402", "信贷风险记录不存在");

    private final String code;
    private final String message;
}
