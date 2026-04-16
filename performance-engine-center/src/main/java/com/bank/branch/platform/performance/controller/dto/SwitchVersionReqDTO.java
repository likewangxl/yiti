package com.bank.branch.platform.performance.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 版本切换请求 DTO.
 *
 * <p>POST /api/perf/sys-control/switch-version 的入参. reason 必填.
 */
@Data
@Schema(description = "SysControl 切换版本请求")
public class SwitchVersionReqDTO {

    /** 维度: EMP / ORG / CUST. */
    @Schema(description = "维度: EMP/ORG/CUST", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "scopeDim 不能为空")
    @Pattern(regexp = "^(EMP|ORG|CUST)$", message = "scopeDim 必须是 EMP / ORG / CUST 之一")
    private String scopeDim;

    /** 新版本对应的数据日期. */
    @Schema(description = "新版本的数据日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-04-15")
    @NotNull(message = "dataDate 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataDate;

    /** 新版本号. */
    @Schema(description = "新版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "V2026_04_15")
    @NotBlank(message = "newVersion 不能为空")
    @Size(max = 32, message = "newVersion 长度不能超过 32")
    private String newVersion;

    /** 切换原因 (必填). */
    @Schema(description = "切换原因, 必填", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "reason 不能为空")
    @Size(max = 200, message = "reason 长度不能超过 200")
    private String reason;
}
