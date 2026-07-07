package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 目标值/基础值表 perf_target_value 贫血实体.
 *
 * <p>对齐 DDL：12 列，主键 varchar(32)（planId 统一 String，见模块架构规约 关键设计原则 3）.
 * <p>唯一键：uk_plan_subject_cycle_metric(plan_id, subject_type, subject_id, cycle_key, metric_code)
 * <p>索引：idx_metric_code(metric_code), idx_subject(subject_type, subject_id, cycle_key)
 */
@Data
@TableName("PERF_TARGET_VALUE")
public class PerfTargetValue {

    /** 目标值ID（varchar(32) 主键）. */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 目标方案ID（varchar(32)）. */
    private String planId;

    /** 对象类型：EMP/ORG. */
    private String subjectType;

    /** 对象ID（emp_id/org_code）. */
    private String subjectId;

    /** 周期键：2026 或 2026Q1 等. */
    private String cycleKey;

    /** 指标编码. */
    private String metricCode;

    /** 目标值（decimal(20,4)）. */
    private BigDecimal targetValue;

    /** 基础值（decimal(20,4)，可空）. */
    private BigDecimal baseValue;

    /** 阶段名称（2026-06-17 新增，可空）. */
    private String stageName;

    /** 起始日期（2026-06-17 新增，可空）. */
    private LocalDate startDate;

    /** 截止日期（2026-06-17 新增，可空）. */
    private LocalDate endDate;

    /**
     * 归属员工 ID（V1.4 S2.1 新增，SELF / SELF_ASSIGNED scope 列）.
     *
     * <p>V1.4 前 ScopeColumns 降级到 created_by 作为 owner 兜底；V1.4 S2 引入独立字段后
     * SELF scope 可精确匹配"我负责的目标值"而非"我上传的目标值".
     */
    private String ownerEmpId;

    /**
     * 归属机构编码（V1.4 S2.1 新增，ORG / ORG_SUBTREE scope 列）.
     *
     * <p>V1.4 前因无此列，ORG scope 被迫降级到 created_by（语义错）；V1.4 S2 真实支持
     * 机构维度过滤（分行管理员可见本分行目标值）.
     */
    private String ownerOrgCode;

    /** 创建人. */
    private String createdBy;

    /** 创建时间. */
    private LocalDateTime createdTime;

    /** 更新人. */
    private String updatedBy;

    /** 更新时间. */
    private LocalDateTime updatedTime;
}
