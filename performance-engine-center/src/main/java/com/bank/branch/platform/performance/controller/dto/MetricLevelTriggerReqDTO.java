package com.bank.branch.platform.performance.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 按指标级别手动重算请求。
 *
 * <p>客户端只提交业务级别，不允许提交治理任务的 jobKey/jobId；级别到固定任务的映射由绩效服务端维护。</p>
 */
@Data
@Schema(description = "按指标级别手动重算请求")
public class MetricLevelTriggerReqDTO {

    /** 指标级别，仅允许 1/2/3。 */
    @Schema(description = "指标级别：1/2/3", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "level 不能为空")
    @Min(value = 1, message = "level 必须为 1/2/3")
    @Max(value = 3, message = "level 必须为 1/2/3")
    private Integer level;

    /** 待计算数据日期。 */
    @Schema(description = "数据日期 yyyy-MM-dd", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-08-17")
    @NotNull(message = "dataDate 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataDate;

    /** 手动重算原因，写入独立审计日志并透传到任务触发参数。 */
    @Schema(description = "重算原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "reason 不能为空")
    @Size(max = 500, message = "reason 不能超过 500 字")
    private String reason;

    /** 业绩分配日期，仅 1 级指标允许提供。 */
    @Schema(description = "业绩分配日期 yyyy-MM-dd（仅 1 级指标可选）")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate allocDate;
}
