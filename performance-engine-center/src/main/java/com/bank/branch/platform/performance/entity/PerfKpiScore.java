package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * KPI 计分明细结果表 PERF_KPI_SCORE 贫血实体.
 *
 * <p>由"KPI 分值计算"后台任务 / 前端重算接口产出。粒度为
 * （数据日期 · KPI方案 · 指标 · 对象），每行记录一个对象在一个指标上的
 * 实际值 / 权重 / 目标值 / 基础值 / 得分。
 *
 * <p>唯一键 {@code uk_date_scheme_metric_subject(data_date, scheme_code, metric_code,
 * subject_type, subject_id)}，重算走 INSERT ... ON DUPLICATE KEY UPDATE 幂等。
 */
@Data
@TableName("PERF_KPI_SCORE")
public class PerfKpiScore {

    /** 主键（bigint AUTO_INCREMENT）. */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 数据日期. */
    private LocalDate dataDate;

    /** KPI 方案编码. */
    private String schemeCode;

    /** 指标编码. */
    private String metricCode;

    /** 对象类型 EMP/ORG/CUST. */
    private String subjectType;

    /** 对象ID（emp_id/org_code/cust_id）. */
    private String subjectId;

    /** 实际值（指标结果表槽位值）. */
    private BigDecimal actualValue;

    /** 权重. */
    private BigDecimal weight;

    /** 目标值（未匹配默认 0）. */
    private BigDecimal targetValue;

    /** 基础值（未匹配默认 0）. */
    private BigDecimal baseValue;

    /** 得分. */
    private BigDecimal score;

    /** 创建时间（DB 默认值填充）. */
    private LocalDateTime createdTime;

    /** 更新时间（DB ON UPDATE 填充）. */
    private LocalDateTime updatedTime;
}
