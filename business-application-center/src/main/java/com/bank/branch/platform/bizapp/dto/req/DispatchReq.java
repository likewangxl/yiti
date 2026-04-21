package com.bank.branch.platform.bizapp.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 派单请求 DTO（承接部门秘书使用）。
 */
@Data
public class DispatchReq {

    /** 被派人工号（必填） */
    @NotBlank(message = "assignedEmpId不能为空")
    private String assignedEmpId;

    /** 派单备注（可选，文档 §D.2） */
    private String dispatchRemark;
}
