package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指标定义表 perf_metric_def 贫血实体.
 *
 * <p>对齐 DDL：20 列，主键 varchar(32)。
 * <p>唯一键：uk_metric_code(metric_code)
 * <p>索引：idx_dim_level / idx_status / idx_val_slot
 */
@Data
@TableName("PERF_METRIC_DEF")
public class PerfMetricDef {

    /** 指标ID（varchar(32) 主键）. */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 指标编码（唯一）. */
    private String metricCode;

    /** 指标中文名称. */
    private String metricName;

    /** 指标英文名称. */
    private String metricNameEn;

    /** 指标口径说明. */
    private String metricDesc;

    /** 基础维度：EMP / ORG / CUST. */
    private String baseDim;

    /** 指标层级：1 / 2 / 3. */
    private Integer metricLevel;

    /** 计算频率：DAY / MONTH / QUARTER / YEAR. */
    private String calcFreq;

    /** 计算方式：AUTO / MANUAL. */
    private String calcMode;

    /** 计算逻辑类型：SQL / PROC / EXPR / SUMMARY. */
    private String calcLogicType;

    /** 一级指标 SQL / 存储过程文本. */
    private String sqlText;

    /** 二/三级指标表达式. */
    private String exprText;

    /** 机构汇总规则：SUM / AVG / MAX / MIN / COUNT. */
    private String summaryRule;

    /** 引用指标列表 JSON 数组字符串. */
    private String refMetricCodes;

    /** 宽表槽位（1..200，null 表示未分配）. */
    private Integer valSlot;

    /** 状态：ACTIVE / DISABLED. */
    private String status;

    /** 创建人. */
    private String createdBy;

    /** 创建时间. */
    private LocalDateTime createdTime;

    /** 更新人. */
    private String updatedBy;

    /** 更新时间. */
    private LocalDateTime updatedTime;

    // ===== V1.0.3 新增字段（Task B4）=====

    /** 单位：元/万元/%. */
    private String unit;

    /** 小数位数，默认 2. */
    private Integer decimalPlaces;

    /** 软删除标志：0=存在，1=已删除. */
    private Integer deleted;

    /** 指标描述（较详细的口径说明，区别于 metric_desc）. */
    private String description;

    // ===== V1.7 指标级调度改造 =====

    /** 自定义 cron 表达式；留空按 calc_freq 推导默认 (V1.7). */
    private String cronExpr;

    /** EXPR/GROOVY 类型主体集合 SQL；SQL/PROC/SUMMARY 类型不需要 (V1.7). */
    private String subjectSql;

    /** 最近一次自动调度执行时间 (V1.7). */
    private java.time.LocalDateTime lastRunTime;
}
