package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 触发 KPI 分值计算请求 DTO（考核计算页面"触发"按钮）.
 *
 * <p>{@code reason} 由 {@code @AuditLog} 切面反射 {@code getReason()} 记入审批日志。
 */
@Data
@Schema(description = "触发 KPI 分值计算请求")
public class KpiScoreCalcReqDTO {

    /** 数据日期（yyyy-MM-dd，必填）. */
    @Schema(description = "数据日期 yyyy-MM-dd", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "数据日期不能为空")
    private String dataDate;

    /** KPI 方案编码（必填）. */
    @Schema(description = "KPI 方案编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "KPI方案编码不能为空")
    private String schemeCode;

    /** 触发原因（必填，记入审批日志）. */
    @Schema(description = "触发原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "触发原因不能为空")
    @Size(max = 500, message = "触发原因长度不能超过 500")
    private String reason;
}
