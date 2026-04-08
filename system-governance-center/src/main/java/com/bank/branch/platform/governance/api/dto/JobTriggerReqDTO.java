package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务手动触发请求DTO
 */
@Data
public class JobTriggerReqDTO {

    /**
     * 触发原因（高危动作必填）
     */
    @NotBlank(message = "触发原因不能为空")
    @Size(max = 500, message = "触发原因长度不能超过500")
    private String reason;
}