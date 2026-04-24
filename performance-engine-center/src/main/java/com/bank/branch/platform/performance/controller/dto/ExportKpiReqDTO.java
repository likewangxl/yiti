package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * KPI 导出请求 DTO（V1.2 Task Q6.4）.
 */
@Data
@Schema(description = "KPI 导出请求")
public class ExportKpiReqDTO {

    @NotBlank(message = "cycleType 必填")
    @Schema(description = "周期类型（MONTHLY/QUARTERLY）")
    private String cycleType;

    @NotNull(message = "cycleDate 必填")
    @Schema(description = "周期日期")
    private LocalDate cycleDate;

    @NotNull(message = "asOfDate 必填")
    @Schema(description = "基准日")
    private LocalDate asOfDate;

    @Schema(description = "数据版本（可选）")
    private String dataVersion;
}
