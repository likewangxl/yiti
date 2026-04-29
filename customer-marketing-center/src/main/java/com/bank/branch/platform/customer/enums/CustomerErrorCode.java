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

    // 400 参数/业务错误
    TAG_NAME_BLANK("CUST-40001", "标签名称不能为空"),
    TAG_CODE_BLANK("CUST-40002", "标签编码不能为空"),
    LEAD_NOT_DRAFT("CUST-40003", "线索非草稿状态，不允许编辑"),
    LEAD_NOT_SUBMITTABLE("CUST-40004", "线索状态不允许提交审批"),
    IMPORT_FILE_EMPTY("CUST-40005", "导入文件为空"),
    IMPORT_ROW_LIMIT_EXCEEDED("CUST-40006", "导入数据超过行数限制"),
    TOUCH_TASK_NOT_PENDING("CUST-40007", "触达任务非待处理状态"),
    TRANSFER_REASON_REQUIRED("CUST-40008", "转交原因不能为空"),
    CANCEL_REASON_REQUIRED("CUST-40009", "取消原因不能为空"),
    TOUCH_TASK_ILLEGAL_TRANSITION("CUST-40010", "触达任务非法状态转移"),

    // 403 权限/越权（V1.0 仅按需补齐 reTouch 必需，其余由批次 C 补全）
    CLAIM_ORG_FORBIDDEN("CUST-40305", "无权操作非本机构认领关系"),

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

    // 500 内部错误
    INTERNAL_ERROR("CUST-50001", "客户营销服务内部错误"),
    WORKFLOW_ERROR("CUST-50002", "工作流调用异常");

    private final String code;
    private final String message;
}
