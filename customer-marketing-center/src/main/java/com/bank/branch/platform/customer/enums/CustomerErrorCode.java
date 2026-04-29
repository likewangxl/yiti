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
    TAG_CODE_BLANK("CUST-40002", "标签编码不能为空"),
    LEAD_NOT_SUBMITTABLE("CUST-40004", "线索状态不允许提交审批"),
    IMPORT_FILE_EMPTY("CUST-40005", "导入文件为空"),
    TOUCH_TASK_NOT_PENDING("CUST-40007", "触达任务非待处理状态"),
    TRANSFER_REASON_REQUIRED("CUST-40008", "转交原因不能为空"),
    CANCEL_REASON_REQUIRED("CUST-40009", "取消原因不能为空"),
    TOUCH_TASK_ILLEGAL_TRANSITION("CUST-40010", "触达任务非法状态转移"),

    // 403 权限/越权（P1C 2026-04-29 落地：40301/40305/40306/40307 触发逻辑；40302/40303/40304 占位待 V1.x 补齐）
    LEAD_EDIT_FORBIDDEN("CUST-40301", "无权编辑非草稿状态线索"),
    TOUCH_TASK_ACCESS_FORBIDDEN("CUST-40302", "无权访问非本人触达任务"),
    CUSTOMER_DELETE_NEED_APPROVAL("CUST-40303", "删除客户需先通过审批"),
    HISTORY_ACCESS_FORBIDDEN("CUST-40304", "无权访问跨机构历史"),
    CLAIM_ORG_FORBIDDEN("CUST-40305", "无权操作非本机构认领关系"),
    TRANSFER_ROLE_MISMATCH("CUST-40306", "转交接收人角色不符"),
    TRANSFER_ORG_MISMATCH("CUST-40307", "转交接收人不在同一机构"),

    // 404 资源不存在
    TAG_NOT_FOUND("CUST-40401", "标签不存在"),
    LEAD_NOT_FOUND("CUST-40402", "线索不存在"),
    CUSTOMER_NOT_FOUND("CUST-40403", "客户不存在"),
    CLAIM_NOT_FOUND("CUST-40404", "认领记录不存在"),
    TOUCH_TASK_NOT_FOUND("CUST-40405", "触达任务不存在"),
    BATCH_NOT_FOUND("CUST-40406", "导入批次不存在"),

    // 409 冲突（按编号递增编排；40907/40910/40911 暂留空给批次 B/C 待补 422 系列时占用）
    TAG_NAME_DUPLICATE("CUST-40901", "标签名称已存在"),
    TAG_CODE_DUPLICATE("CUST-40902", "标签编码已存在"),
    LEAD_NO_DUPLICATE("CUST-40903", "线索编号已存在"),
    CUSTOMER_ALREADY_CLAIMED("CUST-40904", "客户已被该机构认领"),
    TOUCH_LOG_DUPLICATE("CUST-40905", "触达日志重复提交"),
    TAG_CODE_IMMUTABLE("CUST-40906", "标签编码创建后不可修改"),
    RE_TOUCH_HAS_RUNNING("CUST-40908", "触达任务已有进行中，不可重新发起"),
    RE_TOUCH_LAST_NOT_FINISHED("CUST-40909", "最近触达任务未完成，不可重新发起"),
    CUSTOMER_HAS_RUNNING_PROCESS("CUST-40912", "客户存在在途流程不允许删除"),

    // 422 业务校验失败（按编号递增编排；CUST-42201 占位待 V1.x 行级校验落地，详见 LeadImportService.preview TODO）
    LEAD_IMPORT_VALIDATION_FAILED("CUST-42201", "线索导入数据校验失败"),
    TAG_IMPORT_VALIDATION_FAILED("CUST-42202", "标签导入客户数据校验失败"),
    IMPORT_FILE_FORMAT_INVALID("CUST-42203", "导入文件格式错误，仅支持 csv/xlsx/xls"),
    IMPORT_FILE_TOO_LARGE("CUST-42204", "导入文件过大，最大 10MB"),
    IMPORT_ROWS_TOO_MANY("CUST-42205", "导入行数过多，最大 5000 行"),
    TOUCH_LOG_CONTENT_REQUIRED("CUST-42206", "触达日志缺少必填内容，文字与照片不能同时为空"),
    TOUCH_LOG_PHOTO_LIMIT_EXCEEDED("CUST-42207", "触达照片数量超限，最多 9 张"),
    TOUCH_LOG_PHOTO_FORMAT_INVALID("CUST-42208", "触达照片格式不支持，仅支持 jpg/png/heic"),

    // 500 内部错误
    INTERNAL_ERROR("CUST-50001", "客户营销服务内部错误"),
    WORKFLOW_ERROR("CUST-50002", "工作流调用异常");

    private final String code;
    private final String message;
}
