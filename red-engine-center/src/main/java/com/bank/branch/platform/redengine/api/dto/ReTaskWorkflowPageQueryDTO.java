package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务工作台分页查询条件。
 *
 * <p>报送员、支部书记和组织审核员共用字段，但服务层会按角色重新计算数据范围；
 * 请求中的筛选项只用于缩小已经授权的 assignment 集合。</p>
 */
@Data
@Schema(description = "任务工作台分页查询")
public class ReTaskWorkflowPageQueryDTO {

    @Min(value = 1, message = "页码必须从1开始")
    private long pageNo = 1;

    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 200, message = "每页条数不能超过200")
    private long pageSize = 20;

    /** 任务标题、说明或支部名称关键字。 */
    private String keyword;

    /** 前端任务管理筛选使用的任务标题别名。 */
    private String title;

    /** 任务性质：SCHEDULED/TEMPORARY。 */
    private ReTaskNature taskNature;

    /** 前端兼容字段：nature 与 taskNature 等价。 */
    private ReTaskNature nature;

    /** 业务类型：FOUR_DIMENSION/GENERAL。 */
    private ReTaskBusinessType businessType;

    /** 周期类型。 */
    private ReTaskCycleType cycleType;

    /** 前端兼容字段：cycle 与 cycleType 等价。 */
    private ReTaskCycleType cycle;

    /** assignment 状态。 */
    private ReTaskAssignmentStatus assignmentStatus;

    /** 当前提交状态。 */
    private ReTaskSubmissionStatus submissionStatus;

    private Long branchId;
    private LocalDateTime submittedStartAt;
    private LocalDateTime submittedEndAt;

    /** 将 taskNature/nature、cycleType/cycle 两组兼容字段归一化。 */
    public ReTaskNature resolvedTaskNature() {
        return taskNature != null ? taskNature : nature;
    }

    /** 将 cycleType/cycle 两组兼容字段归一化。 */
    public ReTaskCycleType resolvedCycleType() {
        return cycleType != null ? cycleType : cycle;
    }

    /** 将 title/keyword 两组标题筛选字段归一化。 */
    public String resolvedKeyword() {
        return hasText(title) ? title : keyword;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
