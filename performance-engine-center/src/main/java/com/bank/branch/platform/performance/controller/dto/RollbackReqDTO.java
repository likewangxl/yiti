package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 版本回滚请求 DTO（V1.2 Q1.2）.
 *
 * <p>POST /api/perf/sys-control/rollback 的入参。
 * reason 必填（高危操作审计要求，@AuditLog reasonRequired=true）。
 */
@Data
@Schema(description = "SysControl 回滚到历史版本请求")
public class RollbackReqDTO {

    /** 维度: EMP / ORG / CUST. */
    @Schema(description = "维度: EMP/ORG/CUST", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "scopeDim 不能为空")
    @Pattern(regexp = "^(EMP|ORG|CUST)$", message = "scopeDim 必须是 EMP / ORG / CUST 之一")
    private String scopeDim;

    /** 回滚到的历史版本号. */
    @Schema(description = "回滚到的历史版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "V2026_04_01")
    @NotBlank(message = "rollbackTo 不能为空")
    @Size(max = 32, message = "rollbackTo 长度不能超过 32")
    private String rollbackTo;

    /** 回滚原因（必填，写入 remark + 审计日志）. */
    @Schema(description = "回滚原因（必填，用于审计）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "reason 不能为空")
    @Size(max = 500, message = "reason 长度不能超过 500")
    private String reason;
}
