package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 字典状态更新请求DTO（A.6）
 */
@Data
public class DictStatusReqDTO {

    /** 目标状态（ACTIVE/DISABLED） */
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ACTIVE|DISABLED", message = "状态值必须是 ACTIVE 或 DISABLED")
    private String status;
}
