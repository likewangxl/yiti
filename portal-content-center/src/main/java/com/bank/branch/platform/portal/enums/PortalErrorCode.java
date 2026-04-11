package com.bank.branch.platform.portal.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * portal-content-center V1 切片错误码常量
 *
 * <p>权威来源：{@code docs/superpowers/specs/2026-04-11-portal-content-center-v1-slice-design.md §4.11}
 * （V1 切片使用的子集），上游回溯至 {@code docs/modules/portal-content-center/03-接口设计与报文.md §I}。</p>
 *
 * <p>本 V1 切片相对 03 §I 的偏离（已在 spec 附录 B 登记）：</p>
 * <ul>
 *   <li><b>PORTAL-40905</b>（产品仍被员工引用，不可删除）：03 §I 未登记，spec r2 根据 06 §3.4 的
 *       D.6 前置检查场景新增。附录 B4 登记。</li>
 *   <li><b>PORTAL-42200</b>（参数校验失败）：通用码，对应 common-dev-guide §2.3 的 SYS-40001
 *       在 portal 命名空间中的镜像；spec §4.11 列入 V1 切片错误码清单。</li>
 * </ul>
 *
 * <p>仅包含 V1 切片实际使用的 9 个错误码；其他 portal 错误码（导航、文档、通讯录等）将在
 * V2 切片实现时按需追加。</p>
 */
@Getter
@AllArgsConstructor
public enum PortalErrorCode {

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
