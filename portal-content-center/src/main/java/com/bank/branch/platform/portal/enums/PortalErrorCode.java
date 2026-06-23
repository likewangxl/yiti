package com.bank.branch.platform.portal.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * portal-content-center 全域错误码常量
 *
 * <p>权威来源：{@code docs/modules/portal-content-center/03-接口设计与报文.md §I}。
 * 涵盖产品域、导航域、通讯录域、文档域及通用错误码。</p>
 */
@Getter
@AllArgsConstructor
public enum PortalErrorCode {

    // ===== 产品域 =====
    PRODUCT_NOT_FOUND          ("PORTAL-40003", "产品不存在"),
    NO_RIGHT_TO_PRODUCT_DEPT   ("PORTAL-40302", "无权维护非本机构产品"),
    PRODUCT_CODE_DUPLICATE     ("PORTAL-40901", "产品代码已存在"),
    EMPLOYEE_RESIGNED          ("PORTAL-40902", "员工已离职"),
    PRODUCT_RESPONSIBLE_LIMIT  ("PORTAL-40904", "负责产品数量超限"),
    PRODUCT_STILL_REFERRED     ("PORTAL-40905", "产品仍被员工引用，不可删除"),

    // ===== 导航域 =====
    NAV_NOT_FOUND              ("PORTAL-40001", "导航不存在"),
    NAV_NAME_DUPLICATE         ("PORTAL-40903", "导航名称在同一分类下已存在"),
    NAV_URL_INVALID            ("PORTAL-42202", "导航 URL 格式非法"),

    // ===== 通讯录域 =====
    EMPLOYEE_NOT_FOUND         ("PORTAL-40002", "通讯录员工不存在"),
    NO_RIGHT_TO_EDIT_OTHER     ("PORTAL-40301", "无权编辑本人以外的通讯录"),

    // ===== 文档域 =====
    DOC_NOT_FOUND              ("PORTAL-40401", "文档不存在"),
    NO_RIGHT_TO_DOC            ("PORTAL-40304", "无权维护文档"),

    // ===== 担保信息域 =====
    GUARANTEE_NOT_FOUND        ("PORTAL-40006", "担保信息不存在"),

    // ===== 通用 =====
    PARAM_INVALID              ("PORTAL-42200", "参数校验失败"),
    FILE_OBJECT_NOT_FOUND      ("PORTAL-42203", "附件对象不存在"),
    EXPORT_ROWS_LIMIT_EXCEEDED ("PORTAL-42207", "导出行数超过限制"),
    EXPORT_FILE_GENERATE_FAIL  ("PORTAL-50002", "导出文件生成失败");

    private final String code;
    private final String message;
}
