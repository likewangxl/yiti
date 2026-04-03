package com.bank.branch.platform.workflow.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 任务审批通过请求 DTO
 * <p>
 * 包含审批意见和节点表单变量。
 * </p>
 */
@Data
public class ApproveReqDTO {

    /** 审批意见 */
    @Size(max = 500, message = "审批意见长度不能超过500")
    private String comment;

    /** 节点表单数据 / 流程变量 */
    private Map<String, Object> variables;
}
