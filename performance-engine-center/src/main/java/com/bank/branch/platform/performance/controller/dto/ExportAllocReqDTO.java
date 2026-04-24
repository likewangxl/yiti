package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 分配关系导出请求 DTO（V1.2 Task Q6.4）.
 */
@Data
@Schema(description = "分配关系导出请求")
public class ExportAllocReqDTO {

    @NotNull(message = "effectiveDate 必填")
    @Schema(description = "时间线基准日")
    private LocalDate effectiveDate;

    @Schema(description = "业务种类（可选）")
    private String bizKind;

    @Schema(description = "员工工号（可选）")
    private String empId;
}
