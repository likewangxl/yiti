package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

/** 任务逾期待执行扣分分页查询条件。 */
@Data
@Schema(description = "任务逾期扣分分页查询")
public class ReOverduePageQueryDTO {

    @Min(value = 1, message = "页码必须从1开始")
    private int pageNo = 1;

    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 200, message = "每页条数不能超过200")
    private int pageSize = 20;

    /** 任务名称、内容、支部或上报人模糊查询。 */
    private String keyword;
    /** 兼容列表页按任务名称单独查询。 */
    private String taskName;
    /** 兼容列表页按支部名称单独查询。 */
    private String branchName;
    /** 兼容列表页按上报人单独查询。 */
    private String submitterName;
    /** FOUR_DIMENSION 或其他任务类型。 */
    private String taskType;
    private Long branchId;
    /** 任务结束时间范围的兼容入参。 */
    private LocalDateTime taskStartAt;
    private LocalDateTime taskEndAt;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
}
