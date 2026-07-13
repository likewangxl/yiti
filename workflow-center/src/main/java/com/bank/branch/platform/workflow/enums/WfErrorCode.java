package com.bank.branch.platform.workflow.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * WF 模块错误码枚举
 * 格式：WF-{HTTP状态码}{序号}
 */
@Getter
@AllArgsConstructor
public enum WfErrorCode {

    // 404 资源不存在
    RESOURCE_NOT_FOUND("WF-40400", "资源不存在"),
    PROCESS_DEF_NOT_FOUND("WF-40401", "流程定义不存在"),
    PROCESS_INSTANCE_NOT_FOUND("WF-40402", "流程实例不存在"),
    TASK_NOT_FOUND("WF-40403", "任务不存在"),
    CANDIDATE_CONF_NOT_FOUND("WF-40404", "候选组配置不存在"),
    TIMEOUT_RULE_NOT_FOUND("WF-40405", "超时规则不存在"),

    // 409 冲突
    BUSINESS_KEY_ALREADY_RUNNING("WF-40901", "该业务已有运行中的流程"),
    TASK_NOT_OWNED("WF-40902", "任务非当前用户"),
    NOT_TASK_ASSIGNEE("WF-40903", "非任务办理人"),
    TASK_ALREADY_CLAIMED("WF-40904", "任务已被签收"),
    PROCESS_NOT_RUNNING("WF-40905", "流程实例不存在或已结束"),
    FLOW_PUBLISH_VALIDATION_FAILED("WF-40906", "流程图发布校验未通过"),
    TRANSFER_ALREADY_PENDING("WF-40910", "该任务已有待认领的转交"),
    TRANSFER_RECEIVER_NOT_IN_ORG("WF-40911", "接收人不在本机构"),
    TRANSFER_RECEIVER_NOT_CANDIDATE("WF-40912", "接收人无该节点办理资格"),

    // 500 内部错误
    ENGINE_ERROR("WF-50001", "流程引擎异常");

    private final String code;
    private final String message;
}
