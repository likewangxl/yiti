package com.bank.branch.platform.performance.mapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 宽表"按数据日期取某 slot 全对象值"的行投影.
 *
 * <p>用于 KPI 分值计算：一次取回某 {@code data_date} 下某指标槽位的所有对象实际值。
 * {@code subjectId} 为 emp_id / org_code / cust_id（按维度），{@code value} 为该槽位值。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubjectSlotValueRow {

    /** 对象ID（emp_id / org_code / cust_id）. */
    private String subjectId;

    /** 槽位实际值. */
    private BigDecimal value;
}
