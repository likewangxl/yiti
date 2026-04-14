package com.bank.branch.platform.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 流程提交请求 DTO。
 * <p>
 * 由外部 REST 调用提交流程时使用，发起人和机构信息由当前登录态自动补齐。
 * </p>
 */
@Data
public class ProcessSubmitReqDTO {

    /** 业务类型 */
    @NotBlank(message = "业务类型不能为空")
    private String bizType;

    /** 业务 ID */
    @NotBlank(message = "业务ID不能为空")
    private String bizId;

    /** 业务键 */
    @NotBlank(message = "业务键不能为空")
    @Size(max = 100, message = "业务键长度不能超过100")
    private String businessKey;

    /** 流程定义 Key */
    @NotBlank(message = "流程定义KEY不能为空")
    private String processDefinitionKey;

    /** 流程标题 */
    @NotBlank(message = "流程标题不能为空")
    @Size(max = 200, message = "流程标题长度不能超过200")
    private String title;

    /** 额外流程变量 */
    private Map<String, Object> variables;
}
