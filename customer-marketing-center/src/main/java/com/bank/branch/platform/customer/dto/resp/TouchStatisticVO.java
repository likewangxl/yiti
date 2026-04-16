package com.bank.branch.platform.customer.dto.resp;

import lombok.Data;

/**
 * 触达任务状态统计视图对象。
 * <p>
 * 由 TouchReportMapper 按 task_status 分组统计生成，
 * 用于触达报告统计接口（状态分布饼图/汇总数据）。
 * </p>
 */
@Data
public class TouchStatisticVO {

    /** 任务状态：PENDING/SUCCESS/CANCELLED */
    private String status;

    /** 该状态下的任务数量 */
    private Long count;
}
