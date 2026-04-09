package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 配置更新请求DTO
 */
@Data
public class ConfigUpdateReqDTO {

    /** 配置值（必填） */
    @NotBlank(message = "配置值不能为空")
    private String configValue;

    /** 修改原因（高危动作必填） */
    @NotBlank(message = "修改原因不能为空")
    @Size(max = 500, message = "修改原因长度不能超过500")
    private String reason;

    /** 备注（选填） */
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
