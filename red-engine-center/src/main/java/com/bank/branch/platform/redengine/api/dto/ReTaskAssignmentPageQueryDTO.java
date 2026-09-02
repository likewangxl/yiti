package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

/** 任务详情中党支部填报情况的分页查询条件。 */
@Data
@Schema(description = "任务支部分配分页查询")
public class ReTaskAssignmentPageQueryDTO {

    /** 页码，从 1 开始。 */
    @Min(value = 1, message = "页码必须从1开始")
    @Schema(description = "页码")
    private long pageNo = 1;

    /** 每页条数。 */
    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 200, message = "每页条数不能超过200")
    @Schema(description = "每页条数")
    private long pageSize = 20;

    /** 支部名称或填报人关键字。 */
    @Schema(description = "支部名称/填报人关键字")
    private String keyword;

    /** 可选的党支部过滤条件。 */
    @Schema(description = "党支部 ID")
    private Long branchId;

    /** 可选的支部任务状态。 */
    @Schema(description = "支部任务状态")
    private ReTaskAssignmentStatus status;

    /** 填报时间起点。 */
    @Schema(description = "填报时间起点")
    private LocalDateTime submittedStartAt;

    /** 填报时间终点。 */
    @Schema(description = "填报时间终点")
    private LocalDateTime submittedEndAt;
}
