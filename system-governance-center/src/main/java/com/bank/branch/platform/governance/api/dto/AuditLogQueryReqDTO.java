package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 审计日志查询请求
 */
@Data
public class AuditLogQueryReqDTO {

    /** 操作人工号（精确匹配） */
    private String empId;

    /** 业务类型（精确匹配） */
    private String bizType;

    /** 业务动作（精确匹配） */
    private String bizAction;

    /** 开始时间（yyyy-MM-dd 格式） */
    private String startTime;

    /** 结束时间（yyyy-MM-dd 格式） */
    private String endTime;

    /** 模糊搜索关键词 */
    private String keyword;
}
