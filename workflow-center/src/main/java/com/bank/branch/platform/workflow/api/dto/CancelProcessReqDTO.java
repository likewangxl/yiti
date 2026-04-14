package com.bank.branch.platform.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 流程撤回请求 DTO。
 */
@Data
public class CancelProcessReqDTO {

    /** 撤回原因 */
    @NotBlank(message = "撤回原因不能为空")
    @Size(max = 500, message = "撤回原因长度不能超过500")
    private String reason;
}
