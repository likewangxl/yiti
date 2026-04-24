package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 指标宽表导出请求 DTO（V1.2 Task Q6.4）.
 */
@Data
@Schema(description = "指标宽表导出请求")
public class ExportMetricReqDTO {

    @NotEmpty(message = "metricCodes 必填且非空")
    @Schema(description = "指标编码列表")
    private List<String> metricCodes;

    @NotBlank(message = "baseDim 必填")
    @Schema(description = "基础维度（EMP / ORG）")
    private String baseDim;

    @NotNull(message = "dataDate 必填")
    @Schema(description = "数据日期")
    private LocalDate dataDate;

    @NotBlank(message = "version 必填")
    @Schema(description = "数据版本")
    private String version;

    @Schema(description = "ORG 维度必填：机构编码列表")
    private List<String> orgCodes;
}
