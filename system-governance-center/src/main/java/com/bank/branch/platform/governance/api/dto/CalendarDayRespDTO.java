package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 日历天响应DTO（B.1按月查询返回）
 */
@Data
public class CalendarDayRespDTO {
    /** 日期（yyyy-MM-dd 格式） */
    private String day;
    /** 是否工作日 */
    private Boolean isWorkday;
    /** 星期几（1=周一 ... 7=周日） */
    private Integer dayOfWeek;
    /** 备注 */
    private String remark;
}
