package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 日历天传输对象
 * 用于 CalendarApi 对外接口的数据传输
 */
@Data
public class CalendarDayDTO {

    /** 日期 */
    private LocalDate day;

    /** 是否工作日：1-工作日, 0-休息日 */
    private Integer isWorkday;

    /** 备注 */
    private String remark;
}
