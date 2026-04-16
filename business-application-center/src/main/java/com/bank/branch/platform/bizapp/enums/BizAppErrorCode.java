package com.bank.branch.platform.bizapp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务申请中心错误码枚举。
 * 格式：BIZ-{HTTP状态码后两位}{序号}
 */
@Getter
@AllArgsConstructor
public enum BizAppErrorCode {

    // 403 权限错误
    CUSTOMER_NOT_VALID("BIZ-40301", "客户非有效公司客户"),
    NOT_TOUCH_TASK_ASSIGNEE("BIZ-40302", "非触达任务执行人"),
    CUSTOMER_NOT_CLAIMED_BY_ORG("BIZ-40303", "客户未被本机构认领"),
    NOT_SUPPORT_DEPT_MEMBER("BIZ-40304", "非承接部门人员/非当前承接人"),
    NOT_APPLY_CREATOR("BIZ-40305", "非申请创建人无权操作"),

    // 404 资源不存在
    APPLY_NOT_FOUND("BIZ-40401", "申请不存在"),

    // 409 冲突/业务规则
    PRODUCT_NOT_SUPPORT_AVAILABLE("BIZ-40901", "产品不支持中场支持"),
    SCENARIO_B_MISSING_DEPT("BIZ-40902", "场景B缺少supportDeptId"),
    EMPTY_PRODUCT_AND_DEMAND("BIZ-40903", "productIds和otherDemand都为空"),
    INVALID_DICT_VALUE("BIZ-40904", "字典值不合法"),
    EXPOSURE_EXCEEDS_CREDIT("BIZ-40905", "敞口金额大于授信金额"),
    DUPLICATE_SUPPORT_REQUEST("BIZ-40906", "同客户同产品重复申请"),
    PARALLEL_APPLY_OVER_LIMIT("BIZ-40907", "并行申请超限"),

    // 422 校验错误
    NODE_FORM_REQUIRED_MISSING("BIZ-42201", "节点表单必填字段缺失"),
    NODE_FORM_CONDITION_FAIL("BIZ-42202", "节点表单条件必填校验失败"),
    EXPORT_ROW_LIMIT_EXCEEDED("BIZ-42207", "导出行数超上限"),
    INVALID_STATUS_TRANSITION("BIZ-42301", "非法状态迁移"),
    NOT_DRAFT_STATUS("BIZ-42303", "申请非草稿状态不可编辑"),

    // 500 内部错误
    WORKFLOW_CALL_ERROR("BIZ-50001", "工作流调用异常"),
    EXPORT_GENERATE_ERROR("BIZ-50002", "导出文件生成失败");

    private final String code;
    private final String message;
}
