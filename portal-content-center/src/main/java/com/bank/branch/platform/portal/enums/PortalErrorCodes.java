package com.bank.branch.platform.portal.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * portal-content-center V1 错误码常量
 * 权威来源：docs/modules/portal-content-center/03-接口设计与报文.md §I
 * + 1 个 V1 spec 新增码 PORTAL-40905（来自 06 §3.4 的"产品仍被员工引用"场景）
 */
@Getter
@AllArgsConstructor
public enum PortalErrorCodes {

    PRODUCT_NOT_FOUND          ("PORTAL-40003", "产品不存在"),
    NO_RIGHT_TO_PRODUCT_DEPT   ("PORTAL-40302", "无权维护非本机构产品"),
    PRODUCT_CODE_DUPLICATE     ("PORTAL-40901", "产品代码已存在"),
    EMPLOYEE_RESIGNED          ("PORTAL-40902", "员工已离职"),
    PRODUCT_STILL_REFERRED     ("PORTAL-40905", "产品仍被员工引用，不可删除"),
    PARAM_INVALID              ("PORTAL-42200", "参数校验失败"),
    FILE_OBJECT_NOT_FOUND      ("PORTAL-42203", "附件对象不存在"),
    EXPORT_ROWS_LIMIT_EXCEEDED ("PORTAL-42207", "导出行数超过限制"),
    EXPORT_FILE_GENERATE_FAIL  ("PORTAL-50002", "导出文件生成失败");

    private final String code;
    private final String message;
}
