package com.bank.branch.platform.governance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务手动触发响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobTriggerRespDTO {

    /**
     * 本次执行的日志ID
     */
    private String runLogId;

    /**
     * 任务 KEY
     */
    private String jobKey;

    /**
     * 触发时间
     */
    private String triggerTime;
}