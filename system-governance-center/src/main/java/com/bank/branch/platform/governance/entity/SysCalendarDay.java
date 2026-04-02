package com.bank.branch.platform.governance.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工作日历实体，对应 sys_calendar_day 表。
 * <p>
 * 使用 day（日期）作为自然主键，非 UUID。
 * is_workday 字段：1-工作日，0-休息日。
 * </p>
 */
@Data
public class SysCalendarDay {

    /** 日期（自然主键），对应 day */
    private LocalDate day;

    /** 是否工作日：1-工作日, 0-休息日，对应 is_workday */
    private Integer isWorkday;

    /** 备注（如"国庆节"、"调休"等），对应 remark */
    private String remark;

    /** 创建人，对应 created_by */
    private String createdBy;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新人，对应 updated_by */
    private String updatedBy;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;
}
