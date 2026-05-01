package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * KPI 方案项表 perf_kpi_item 贫血实体.
 *
 * <p>对齐 DDL：8 列，主键 varchar(32).
 * <p>唯一键：uk_scheme_metric(scheme_id, metric_code)
 * <p>索引：idx_scheme_id(scheme_id)
 */
@Data
@TableName("perf_kpi_item")
public class PerfKpiItem {

    /** 项ID（varchar(32) 主键）. */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 所属方案ID. */
    private String schemeId;

    /** 指标编码（人员维度，引用 perf_metric_def.metric_code）. */
    private String metricCode;

    /** 权重（decimal(10,4)）. */
    private BigDecimal weight;

    /** 加倍系数（decimal(10,4)，默认 1）. */
    private BigDecimal multiplier;

    /** 最低分（decimal(10,4)，默认 0）. */
    private BigDecimal minScore;

    /** 最高分（decimal(10,4)，默认 999999）. */
    private BigDecimal maxScore;

    /** 创建时间. */
    private LocalDateTime createdTime;
}
