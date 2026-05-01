package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 目标方案表 perf_target_plan 贫血实体.
 *
 * <p>对齐 DDL：12 列，主键 varchar(32)（id / kpiSchemeId 统一 String，见模块 CLAUDE.md 关键设计原则 3）.
 * <p>唯一键：uk_plan_code(plan_code)
 * <p>索引：idx_status(status)
 */
@Data
@TableName("perf_target_plan")
public class PerfTargetPlan {

    /** 目标方案ID（varchar(32) 主键）. */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 方案编码（唯一）. */
    private String planCode;

    /** 方案名称. */
    private String planName;

    /** 关联 KPI 方案ID（varchar(32)）. */
    private String kpiSchemeId;

    /** 目标维度：EMP/ORG. */
    private String targetDim;

    /** 目标周期：YEAR/QUARTER. */
    private String targetCycle;

    /** 生效日期. */
    private LocalDate effectiveDate;

    /** 状态：ACTIVE/DISABLED. */
    private String status;

    /**
     * 归属员工 ID（V1.4 S2.1 新增，SELF / SELF_ASSIGNED scope 列）.
     *
     * <p>V1.4 前 ScopeColumns 降级到 created_by 作为 owner 兜底；V1.4 S2 引入独立字段后
     * SELF scope 可精确匹配"我负责的方案"而非"我创建的方案"，语义收敛.
     */
    private String ownerEmpId;

    /**
     * 归属机构编码（V1.4 S2.1 新增，ORG / ORG_SUBTREE scope 列）.
     *
     * <p>V1.4 前因无此列，ORG scope 被迫降级到 created_by（语义错）；V1.4 S2 真实支持
     * 机构维度过滤（分行管理员可见本分行方案）.
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
