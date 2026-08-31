package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/** 任务逾期扣分执行结果。 */
@Data
@Schema(description = "任务逾期扣分执行结果")
public class ReTaskDeductionActionRespDTO {

    private Long deductionId;
    private Long assignmentId;
    private Long taskId;
    private Long taskInstanceId;
    private Long branchId;
    private BigDecimal deductionPoints;
    private ReTaskDeductionStatus status;
    private boolean idempotent;
}
