package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务定义请求 DTO。
 * <p>周期、临时任务和任务对象的组合约束由任务领域服务校验；例如周期任务必须有周期
 * 和持续天数，临时任务必须有开始/结束时间，文件类型只有在要求上传文件时才有意义。</p>
 */
@Data
@Schema(description = "红色引擎任务新增请求")
public class ReTaskCreateReqDTO {

    /** 任务标题。 */
    @NotBlank(message = "任务标题不能为空")
    @Schema(description = "任务标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    /** 任务说明。 */
    @NotBlank(message = "任务说明不能为空")
    @Schema(description = "任务说明", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    /** 定时或临时。 */
    @NotNull(message = "任务性质不能为空")
    @Schema(description = "任务性质", requiredMode = Schema.RequiredMode.REQUIRED)
    private ReTaskNature taskNature;

    /** 四大维度或普通任务。 */
    @NotNull(message = "任务业务类型不能为空")
    @Schema(description = "任务业务类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private ReTaskBusinessType businessType;

    /** 定时周期；临时任务为空。 */
    @Schema(description = "定时周期")
    private ReTaskCycleType cycleType;

    /** 周期窗口持续自然日数；首尾包含。 */
    @Min(value = 1, message = "周期持续时间必须大于0")
    @Schema(description = "周期持续时间(自然日)")
    private Integer durationDays;

    /** 定时任务生效起始日期；不填表示从发布后的当前有效周期开始。 */
    @Schema(description = "定时任务生效起始日期")
    private LocalDate effectiveFrom;

    /** 定时任务生效结束日期；不填表示持续有效。 */
    @Schema(description = "定时任务生效结束日期")
    private LocalDate effectiveTo;

    /** 临时任务开始时间。 */
    @Schema(description = "临时任务开始时间")
    private LocalDateTime temporaryStartTime;

    /** 临时任务结束时间。 */
    @Schema(description = "临时任务结束时间")
    private LocalDateTime temporaryEndTime;

    /** 是否要求上传文件。 */
    @NotNull(message = "是否要求上传文件不能为空")
    @Schema(description = "是否要求上传文件", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean requiresFile;

    /** 允许的文件类型编码，须由任务服务按治理字典/文件策略校验。 */
    @Schema(description = "允许上传的文件类型编码")
    private List<String> fileTypeCodes;

    /** 任务对象，至少一项。 */
    @NotEmpty(message = "任务对象不能为空")
    @Valid
    @Schema(description = "任务对象", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ReTaskTargetDTO> targets;

    /** 四大维度任务导出/处理时使用的 RE_ITEM_CODE 明细项。 */
    @Schema(description = "四大维度明细项编码")
    private List<String> itemCodes;
}
