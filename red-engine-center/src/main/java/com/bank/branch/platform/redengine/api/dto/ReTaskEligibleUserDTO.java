package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 任务指定员工候选项。
 *
 * <p>员工 ID 来自 auth 的 {@code PT_USER.USER_ID}，党支部信息来自本域的用户党组织映射，
 * 仅返回已启用且能够解析到党支部的员工，供任务对象选择器展示和提交。</p>
 */
@Data
@Schema(description = "任务可选员工")
public class ReTaskEligibleUserDTO {

    /** 平台用户 ID，创建任务时写入 ReTaskTargetDTO.employeeId。 */
    @Schema(description = "平台用户 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String employeeId;

    /** 平台登录工号。 */
    @Schema(description = "登录工号")
    private String username;

    /** 平台用户中文姓名。 */
    @Schema(description = "中文姓名")
    private String displayName;

    /** 员工归属的党支部 ID。 */
    @Schema(description = "所属党支部 ID")
    private Long branchId;

    /** 员工归属的党支部名称。 */
    @Schema(description = "所属党支部名称")
    private String branchName;
}
