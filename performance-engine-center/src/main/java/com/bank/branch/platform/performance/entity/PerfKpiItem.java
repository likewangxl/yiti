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
@TableName("PERF_KPI_ITEM")
public class PerfKpiItem {

    /** 项ID（varchar(32) 主键）. */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 所属方案ID. */
    private String schemeId;

    /** 指标编码（人员维度，引用 perf_metric_def.metric_code）. */
    private String metricCode;

    /**
     * 指标维度（EMP/ORG/CUST，varchar(8)，可空）.
     *
     * <p>与所选指标 {@code perf_metric_def.base_dim} 一致，新增时随指标固化落库，
     * 便于方案项列表/编辑回显直接展示维度，免去再 join 指标定义表。
     */
    private String baseDim;

    /** 权重（decimal(10,4)）. */
    private BigDecimal weight;

    /** 加倍系数（decimal(10,4)，默认 1）. */
    private BigDecimal multiplier;

    /** 最低分（decimal(10,4)，默认 0）. */
    private BigDecimal minScore;

    /** 最高分（decimal(10,4)，默认 999999）. */
    private BigDecimal maxScore;

    /**
     * 计分公式（varchar(500)，可空）.
     *
     * <p>前端 KpiRules.vue 编辑，可用变量 {@code actual}（实际值）/ {@code target}（目标值）/
     * {@code base}（基础值）/ {@code weight}（权重），支持 {@code min} / {@code max} 函数，
     * 例：{@code min(actual / target * 100, 120)}。KPI 分值计算时按对象代入求值。
     */
    private String formula;

    /** 创建时间. */
    private LocalDateTime createdTime;
}
