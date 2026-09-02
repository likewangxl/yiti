package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 组织管理员执行任务逾期扣分请求。 */
@Data
@Schema(description = "任务逾期扣分执行请求")
public class ReTaskDeductionExecuteReqDTO {

    @NotNull(message = "assignmentId 不能为空")
    private Long assignmentId;

    /** 未填写时使用平台默认扣分值。 */
    @DecimalMin(value = "0.01", message = "扣分分值必须大于0")
    @Digits(integer = 8, fraction = 2, message = "扣分分值格式不正确")
    private BigDecimal deductionPoints;

    @NotBlank(message = "扣分原因不能为空")
    @Size(max = 255, message = "扣分原因不能超过255个字符")
    private String reason;

    /** 客户端幂等键；为空时以 assignmentId 的唯一约束保证单次执行。 */
    @Size(max = 100, message = "幂等键不能超过100个字符")
    private String clientRequestId;
}
