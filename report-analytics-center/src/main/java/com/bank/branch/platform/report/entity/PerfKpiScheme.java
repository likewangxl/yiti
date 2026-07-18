package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * KPI 方案 PERF_KPI_SCHEME 贫血实体（report 模块只读视图，仅承载大屏 KPI_DETAIL 数据源
 * 保存校验与方案下拉所需字段；权威定义在 performance-engine-center）.
 *
 * <p>按 D1 决策大屏数据访问统一直查白名单表，本实体仅做 MyBatis-Plus 条件查询，禁止任何写操作。</p>
 */
@Data
@TableName("PERF_KPI_SCHEME")
public class PerfKpiScheme {

    /** 方案ID（varchar 主键，上游生成） */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 方案编码（唯一） */
    private String schemeCode;

    /** 方案名称 */
    private String schemeName;

    /** 周期类型：MONTHLY/QUARTERLY */
    private String cycleType;

    /** 状态：ACTIVE/DISABLED */
    private String status;
}
