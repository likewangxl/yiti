package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布 KPI 方案请求 DTO (高危操作, reason 必填).
 */
@Data
@Schema(description = "发布 KPI 方案请求 (高危)")
public class PublishKpiSchemeReqDTO {

    /** 审计原因. */
    @Schema(description = "发布原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "reason 不能为空")
    @Size(max = 200, message = "reason 长度不能超过 200")
    private String reason;
}
