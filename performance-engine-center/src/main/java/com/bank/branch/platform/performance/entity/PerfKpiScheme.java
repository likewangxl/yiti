package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * KPI 方案表 perf_kpi_scheme 贫血实体.
 *
 * <p>对齐 DDL：10 列，主键 varchar(32).
 * <p>唯一键：uk_scheme_code(scheme_code)
 * <p>索引：idx_status(status)
 */
@Data
@TableName("perf_kpi_scheme")
public class PerfKpiScheme {

    /** 方案ID（varchar(32) 主键）. */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 方案编码（唯一）. */
    private String schemeCode;

    /** 方案名称. */
    private String schemeName;

    /** 周期：MONTHLY / QUARTERLY. */
    private String cycleType;

    /** 是否向员工开放明细（0=否, 1=是）. */
    private Integer openDetail;

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
}
