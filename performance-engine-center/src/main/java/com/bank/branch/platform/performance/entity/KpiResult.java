package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * KPI 结果表 kpi_result 贫血实体.
 *
 * <p>对齐 V1_0_0__performance_ddl.sql §13：主键 {@code id bigint AUTO_INCREMENT}，
 * 业务唯一键 {@code uk_emp_cycle_asof(emp_id, cycle_type, cycle_date, as_of_date)}。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code cycle_type}：MONTHLY / QUARTERLY 等周期类型</li>
 *   <li>{@code cycle_date}：周期对应日期（如月末）</li>
 *   <li>{@code as_of_date}：计算基准日；"每日一算"模式下与 cycle_date 配合保证幂等</li>
 *   <li>{@code data_version}：指标数据版本（与 sys_control.active_version 对齐）</li>
 *   <li>{@code kpi_total_score}：KPI 总分（decimal(10,4)）</li>
 *   <li>{@code detail_json}：KPI 各项得分明细（longtext，V1.0 存原始 JSON 字符串；
 *       未来若需要结构化访问，可在 Service 层解析为 {@code Map<String, Object>}）</li>
 * </ul>
 */
@Data
@TableName("KPI_RESULT")
public class KpiResult {

    /** 主键（bigint AUTO_INCREMENT）. */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 员工工号. */
    private String empId;

    /** 周期类型：MONTHLY / QUARTERLY. */
    private String cycleType;

    /** 周期日期（如月末）. */
    private LocalDate cycleDate;

    /** 计算基准日（"每日一算"区分键）. */
    private LocalDate asOfDate;

    /** 指标数据版本. */
    private String dataVersion;

    /** KPI 总分（decimal(10,4)）. */
    private BigDecimal kpiTotalScore;

    /** 明细 JSON（可选，longtext）. */
    private String detailJson;

    /** 计算时间（DB CURRENT_TIMESTAMP 默认值填充）. */
    private LocalDateTime calculatedTime;
}
