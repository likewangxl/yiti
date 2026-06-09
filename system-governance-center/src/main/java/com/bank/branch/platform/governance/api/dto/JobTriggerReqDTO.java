package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务手动触发请求DTO
 */
@Data
public class JobTriggerReqDTO {

    /**
     * 触发原因（高危动作必填）
     */
    @NotBlank(message = "触发原因不能为空")
    @Size(max = 500, message = "触发原因长度不能超过500")
    private String reason;

    /**
     * 数据日期（yyyy-MM-dd，可空）.
     *
     * <p>手动触发计算类任务（指标计算 / KPI 计算）时携带，经 JobDataMap 透传给对应 Quartz Job，
     * 用于按指定日期启动计算；为空时计算类任务回退「昨日」（与 cron 自动触发一致）。
     * 非计算类任务（清理等）忽略该值。前端按必输 + 不大于当前日期校验。
     */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "数据日期格式必须为 yyyy-MM-dd")
    private String dataDate;
}