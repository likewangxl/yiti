package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 任务指定员工候选项分页查询条件。 */
@Data
@Schema(description = "任务可选员工分页查询")
public class ReTaskEligibleUserPageQueryDTO {

    /** 页码，从 1 开始。 */
    @Min(value = 1, message = "页码必须从1开始")
    @Schema(description = "页码")
    private long pageNo = 1;

    /** 每页条数。 */
    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页条数不能超过100")
    @Schema(description = "每页条数")
    private long pageSize = 20;

    /** 员工 ID、登录工号或中文姓名包含式匹配。 */
    @Size(max = 100, message = "查询关键字不能超过100个字符")
    @Schema(description = "员工 ID/工号/姓名关键字")
    private String keyword;

    /** 可选的党支部 ID；不传表示当前红色引擎数据范围内全部党支部。 */
    @Schema(description = "所属党支部 ID")
    private Long branchId;
}
