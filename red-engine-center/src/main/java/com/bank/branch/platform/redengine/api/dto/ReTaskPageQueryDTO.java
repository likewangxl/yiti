package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

/** 任务管理列表分页查询条件。 */
@Data
@Schema(description = "任务列表查询")
public class ReTaskPageQueryDTO {

    /** 页码，从 1 开始。 */
    @Min(value = 1, message = "页码必须从1开始")
    @Schema(description = "页码")
    private long pageNo = 1;

    /** 每页条数。 */
    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 200, message = "每页条数不能超过200")
    @Schema(description = "每页条数")
    private long pageSize = 20;

    /** 标题关键字。 */
    @Schema(description = "任务标题")
    private String title;

    /** 任务性质。 */
    @Schema(description = "任务性质")
    private ReTaskNature taskNature;

    /** 任务业务类型。 */
    @Schema(description = "任务业务类型")
    private ReTaskBusinessType businessType;

    /** 周期类型。 */
    @Schema(description = "周期类型")
    private ReTaskCycleType cycleType;

    /** 任务状态。 */
    @Schema(description = "任务状态")
    private ReTaskStatus status;

    /** 发布时间起始日期。 */
    @Schema(description = "发布时间起始日期")
    private LocalDate publishStartDate;

    /** 发布时间结束日期。 */
    @Schema(description = "发布时间结束日期")
    private LocalDate publishEndDate;
}
