package com.bank.branch.platform.performance.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 外部数据处理状态上报请求 DTO（V1.1 Task P6.1）.
 *
 * <p>对齐权威文档 {@code docs/modules/performance-engine-center/03-接口设计与报文.md §G.1}：
 * 外部数据同步系统将 T 日数据导入完成后，调用 {@code POST /api/data-task/status} 上报状态，
 * 本模块依据 dataType 触发对应的 sys_control 版本发布及后续 KPI 计算。
 *
 * <p>字段约束：
 * <ul>
 *   <li>{@code taskId} 全局唯一幂等键（必填, &le; 128），重复上报返回既有结果</li>
 *   <li>{@code dataType} 仅允许 ALLOC_RELATION / EMP_INDEX_RESULT / ORG_INDEX_RESULT / CUST_INDEX_RESULT</li>
 *   <li>{@code dataDate} ISO-8601（yyyy-MM-dd），Jackson 自动解析 LocalDate</li>
 *   <li>{@code status} 仅允许 SUCCESS / FAILED</li>
 *   <li>{@code version} 必填，由外部上游系统自管理的版本号</li>
 *   <li>{@code errorMsg} 仅在 {@code status=FAILED} 时使用</li>
 *   <li>{@code sourceSystem} 可选，来源系统标识（CORE_BANK / ASSET / ...）</li>
 *   <li>{@code timestamp} 可选，外部系统上报时间戳（UTC Instant）</li>
 * </ul>
 */
@Data
@Schema(description = "外部数据任务状态上报请求")
public class DataTaskStatusReqDTO {

    /** 全局唯一任务 ID（幂等键, 必填）. */
    @Schema(description = "全局唯一任务 ID（幂等键）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "taskId 不能为空")
    @Size(max = 128, message = "taskId 长度不能超过 128")
    private String taskId;

    /** 数据类型：ALLOC_RELATION / EMP_INDEX_RESULT / ORG_INDEX_RESULT / CUST_INDEX_RESULT. */
    @Schema(description = "数据类型", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"ALLOC_RELATION", "EMP_INDEX_RESULT", "ORG_INDEX_RESULT", "CUST_INDEX_RESULT"})
    @NotBlank(message = "dataType 不能为空")
    @Pattern(regexp = "ALLOC_RELATION|EMP_INDEX_RESULT|ORG_INDEX_RESULT|CUST_INDEX_RESULT",
            message = "dataType 仅允许 ALLOC_RELATION/EMP_INDEX_RESULT/ORG_INDEX_RESULT/CUST_INDEX_RESULT")
    private String dataType;

    /** 数据日期（ISO yyyy-MM-dd, 必填）. */
    @Schema(description = "数据日期（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "dataDate 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataDate;

    /** 版本号（由上游系统管理）. */
    @Schema(description = "版本号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "version 不能为空")
    @Size(max = 32, message = "version 长度不能超过 32")
    private String version;

    /** 状态：SUCCESS / FAILED. */
    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"SUCCESS", "FAILED"})
    @NotBlank(message = "status 不能为空")
    @Pattern(regexp = "SUCCESS|FAILED", message = "status 仅允许 SUCCESS 或 FAILED")
    private String status;

    /** 行数（可选，用于监控/审计展示）. */
    @Schema(description = "行数")
    private Integer rowCount;

    /** 错误信息（FAILED 时填写；longtext，上限业务侧默认 1000 字）. */
    @Schema(description = "错误信息")
    @Size(max = 1000, message = "errorMsg 长度不能超过 1000")
    private String errorMsg;

    /** 来源系统标识（可选，例如 CORE_BANK / ASSET / ...）. */
    @Schema(description = "来源系统标识")
    @Size(max = 64, message = "sourceSystem 长度不能超过 64")
    private String sourceSystem;

    /** 上报时间戳（ISO-8601 UTC Instant，可选）. */
    @Schema(description = "上报时间戳（ISO-8601 UTC）")
    private Instant timestamp;
}
