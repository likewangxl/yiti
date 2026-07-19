package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 驾驶舱-执行逾期扣分请求 DTO。
 * <p>对应源 redengine {@code CockpitController.executeOverdue} 的两个 {@code @RequestParam}
 * (submitId/deductionPoints)，拍平为强类型请求体；{@code deductionPoints} 未提供时由
 * {@code ReCockpitService.executeOverdue} 兜底默认5分（源 Controller 层的默认值逻辑下沉到 Service，
 * 便于单测覆盖）。本端点为高危操作(执行扣分)，新增 {@link #reason} 字段配合
 * {@code @AuditLog(reasonRequired = true)} 强制留痕，源系统无此字段（本次移植新增）。</p>
 */
@Data
@Schema(description = "驾驶舱-执行逾期扣分请求")
public class ReOverdueExecuteReqDTO {

    /** 上报ID */
    @Schema(description = "上报ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "submitId 不能为空")
    private Long submitId;

    /** 扣分分值(不填默认5分) */
    @Schema(description = "扣分分值(不填默认5分)")
    private BigDecimal deductionPoints;

    /** 执行原因(高危操作必填，审计留痕) */
    @Schema(description = "执行原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "执行原因不能为空")
    private String reason;
}
