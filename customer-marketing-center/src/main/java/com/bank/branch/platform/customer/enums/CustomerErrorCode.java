package com.bank.branch.platform.customer.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户营销中心错误码枚举
 * 格式：CUST-{HTTP状态码}{序号}
 */
@Getter
@AllArgsConstructor
public enum CustomerErrorCode {

    // 400 参数/业务错误（CUST-40003 已废弃，"线索非草稿"语义升级到 CUST-40301 LEAD_EDIT_FORBIDDEN P1C；CUST-40006 已废弃 → CUST-42205）
    TAG_NAME_BLANK("CUST-40001", "标签名称不能为空"),
    LEAD_NOT_SUBMITTABLE("CUST-40004", "线索状态不允许提交审批"),
    IMPORT_FILE_EMPTY("CUST-40005", "导入文件为空"),
    TOUCH_TASK_NOT_PENDING("CUST-40007", "触达任务非待处理状态"),
    TRANSFER_REASON_REQUIRED("CUST-40008", "转交原因不能为空"),
    CANCEL_REASON_REQUIRED("CUST-40009", "取消原因不能为空"),
    TOUCH_TASK_ILLEGAL_TRANSITION("CUST-40010", "触达任务非法状态转移"),
    TAG_REJECT_REASON_REQUIRED("CUST-40011", "标签退回原因不能为空"),
    CROSS_ORG_REASON_REQUIRED("CUST-40012", "跨机构营销申请原因不能为空"),
    TRANSFER_TARGET_REQUIRED("CUST-40013", "至少选择一名接收客户经理"),

    // 403 权限/越权（P1C 2026-04-29 落地：40301/40305/40306/40307 触发逻辑；40302/40303/40304 占位待 V1.x 补齐）
    LEAD_EDIT_FORBIDDEN("CUST-40301", "无权编辑非草稿状态线索"),
    TOUCH_TASK_ACCESS_FORBIDDEN("CUST-40302", "无权访问非本人触达任务"),
    CUSTOMER_DELETE_NEED_APPROVAL("CUST-40303", "删除客户需先通过审批"),
    HISTORY_ACCESS_FORBIDDEN("CUST-40304", "无权访问跨机构历史"),
    CLAIM_ORG_FORBIDDEN("CUST-40305", "无权操作非本机构认领关系"),
    TRANSFER_ROLE_MISMATCH("CUST-40306", "转交接收人角色不符"),
    TRANSFER_ORG_MISMATCH("CUST-40307", "转交接收人不在同一机构"),
    LEAD_WRITE_FORBIDDEN("CUST-40308", "无权编辑不在数据范围内的线索"),
    CROSS_ORG_REVIEW_FORBIDDEN("CUST-40309", "无权审核跨机构营销申请"),
    TRANSFER_ACCESS_FORBIDDEN("CUST-40310", "无权转交非本人或非本机构主办的客户"),
    TAG_REVIEW_FORBIDDEN("CUST-40311", "仅创建人本人或公司部审核人员可审核该标签"),

    // 404 资源不存在
    TAG_NOT_FOUND("CUST-40401", "标签不存在"),
    LEAD_NOT_FOUND("CUST-40402", "线索不存在"),
    CUSTOMER_NOT_FOUND("CUST-40403", "客户不存在"),
    CLAIM_NOT_FOUND("CUST-40404", "认领记录不存在"),
    TOUCH_TASK_NOT_FOUND("CUST-40405", "触达任务不存在"),
    BATCH_NOT_FOUND("CUST-40406", "导入批次不存在"),
    CROSS_ORG_APPLY_NOT_FOUND("CUST-40407", "跨机构营销申请不存在"),
    TRANSFER_LOG_NOT_FOUND("CUST-40408", "客户转交记录不存在"),

    // 409 冲突（按编号递增编排；40907/40910/40911 暂留空给批次 B/C 待补 422 系列时占用）
    TAG_NAME_DUPLICATE("CUST-40901", "标签名称已存在"),
    LEAD_NO_DUPLICATE("CUST-40903", "线索编号已存在"),
    CUSTOMER_ALREADY_CLAIMED("CUST-40904", "当前员工已认领该客户"),
    TOUCH_LOG_DUPLICATE("CUST-40905", "触达日志重复提交"),
    RE_TOUCH_HAS_RUNNING("CUST-40908", "触达任务已有进行中，不可重新发起"),
    RE_TOUCH_LAST_NOT_FINISHED("CUST-40909", "最近触达任务未完成，不可重新发起"),
    CUSTOMER_HAS_RUNNING_PROCESS("CUST-40912", "客户存在在途流程不允许删除"),
    LEAD_CREDIT_CODE_DUPLICATE("CUST-40913", "该统一社会信用代码已存在有效新客户线索"),
    TAG_REVIEW_STATUS_CONFLICT("CUST-40914", "仅待审核标签可以办理"),
    CROSS_ORG_STATUS_CONFLICT("CUST-40915", "仅待审批申请可以办理"),
    CROSS_ORG_ACTIVE_DUPLICATE("CUST-40916", "该客户已有本人待审批或已通过的跨机构营销申请"),
    TRANSFER_TARGET_CURRENT_MANAGER("CUST-40917", "接收客户经理不能包含当前主办客户经理"),

    // 422 业务校验失败（按编号递增编排；CUST-42201 占位待 V1.x 行级校验落地，详见 LeadImportService.preview TODO）
    LEAD_IMPORT_VALIDATION_FAILED("CUST-42201", "线索导入数据校验失败"),
    TAG_IMPORT_VALIDATION_FAILED("CUST-42202", "标签导入客户数据校验失败"),
    IMPORT_FILE_FORMAT_INVALID("CUST-42203", "导入文件格式错误，仅支持 csv/xlsx/xls"),
    IMPORT_FILE_TOO_LARGE("CUST-42204", "导入文件过大，最大 10MB"),
    IMPORT_ROWS_TOO_MANY("CUST-42205", "导入行数过多，最大 5000 行"),
    TOUCH_LOG_CONTENT_REQUIRED("CUST-42206", "触达日志缺少必填内容，文字与照片不能同时为空"),
    TOUCH_LOG_PHOTO_LIMIT_EXCEEDED("CUST-42207", "触达照片数量超限，最多 9 张"),
    TOUCH_LOG_PHOTO_FORMAT_INVALID("CUST-42208", "触达照片格式不支持，仅支持 jpg/png/heic"),
    TOUCH_LOG_PHOTO_REQUIRED("CUST-42213", "至少上传一张触达照片"),
    LEAD_DISTRIBUTION_INVALID("CUST-42209", "线索分配方式或指定客户经理范围不合法"),
    LEAD_MANAGER_INVALID("CUST-42210", "指定人员不是有效客户经理"),
    LEAD_OWNER_MISMATCH("CUST-42211", "主办专属人员与存量客户主办权不一致"),
    LEAD_APPROVAL_RESULT_INVALID("CUST-42212", "审批结果筛选仅支持APPROVED或REJECTED"),
    TAG_NOT_APPROVED("CUST-42214", "仅审核通过且启用的标签可以关联客户"),
    CROSS_ORG_VALIDATION_FAILED("CUST-42215", "当前客户不满足跨机构营销申请条件"),
    TOUCH_RESTRICTED_ACCOUNT_OPENED("CUST-42216", "该企业经判定已开户，无法再创建工作日志"),
    TOUCH_LIMIT_REACHED("CUST-42217", "客户触达次数已达到标签周期上限"),

    // 500 内部错误
    INTERNAL_ERROR("CUST-50001", "客户营销服务内部错误"),
    WORKFLOW_ERROR("CUST-50002", "工作流调用异常");

    private final String code;
    private final String message;
}
