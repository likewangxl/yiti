package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 任务对象请求项。发布服务会把它快照为党支部分配和员工待办。 */
@Data
@Schema(description = "任务对象")
public class ReTaskTargetDTO {

    /** 对象类型。 */
    @NotNull(message = "任务对象类型不能为空")
    @Schema(description = "对象类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private ReTaskTargetType targetType;

    /** 指定党支部时填写。 */
    @Schema(description = "党支部 ID")
    private Long partyOrgId;

    /** 指定员工时填写平台用户 ID。 */
    @Schema(description = "员工平台用户 ID")
    private String employeeId;
}
