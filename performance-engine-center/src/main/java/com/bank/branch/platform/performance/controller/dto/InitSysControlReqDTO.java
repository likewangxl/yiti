package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 版本控制初始化请求 DTO.
 *
 * <p>POST /api/perf/sys-control/init 的入参, reason 必填 (审计要求).
 */
@Data
@Schema(description = "SysControl 初始化请求")
public class InitSysControlReqDTO {

    /** 操作原因 (必填, 用于审计). */
    @Schema(description = "操作原因, 必填", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "reason 不能为空")
    @Size(max = 200, message = "reason 长度不能超过 200")
    private String reason;
}
