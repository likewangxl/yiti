package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

/**
 * 超时规则更新请求 DTO
 */
@Data
public class TimeoutRuleUpdateReqDTO {

    /** 黄灯阈值（工作小时数） */
    private Integer warningHours;

    /** 红灯阈值（工作小时数），必须 > warningHours */
    private Integer timeoutHours;
}
