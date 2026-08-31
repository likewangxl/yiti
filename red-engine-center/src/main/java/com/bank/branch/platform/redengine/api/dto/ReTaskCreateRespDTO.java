package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 任务发布结果，返回定义及首个实例的稳定标识。 */
@Data
@Schema(description = "任务发布结果")
public class ReTaskCreateRespDTO {

    /** 任务定义主键。 */
    @Schema(description = "任务 ID")
    private Long taskId;

    /** 任务业务编号。 */
    @Schema(description = "任务编号")
    private String taskNo;

    /** 发布后的任务状态。 */
    @Schema(description = "任务状态")
    private ReTaskStatus status;

    /** 首个任务实例主键；定时任务为当前/下一有效窗口实例。 */
    @Schema(description = "任务实例 ID")
    private Long instanceId;

    /** 本次生成的支部分配数量。 */
    @Schema(description = "支部分配数")
    private int assignmentCount;

    /** 本次生成的员工待办数量。 */
    @Schema(description = "待办数")
    private int todoCount;
}
