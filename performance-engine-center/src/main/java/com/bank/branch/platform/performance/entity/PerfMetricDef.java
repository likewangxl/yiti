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
@TableName("perf_metric_def")
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
}
