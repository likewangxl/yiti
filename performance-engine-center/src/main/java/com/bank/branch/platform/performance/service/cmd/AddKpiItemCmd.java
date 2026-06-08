package com.bank.branch.platform.performance.service.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 新增 KPI 方案项命令.
 *
 * <p>Service 层聚合单条与批量 (create scheme 时 foreach) 两种入口, 统一用本 Cmd.
 * <p>Controller 层 DTO 做 JSR-303 校验, 到 Service 层只负责业务校验 (重复项、metric 存在性等).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddKpiItemCmd {

    /** 所属方案ID. */
    private String schemeId;

    /** 指标编码（引用 perf_metric_def.metric_code）. */
    private String metricCode;

    /** 指标维度（EMP/ORG/CUST，随指标固化落库，便于展示）. */
    private String baseDim;

    /** 权重（decimal(10,4)）. */
    private BigDecimal weight;

    /** 加倍系数（默认 1）. */
    private BigDecimal multiplier;

    /** 最低分（默认 0）. */
    private BigDecimal minScore;

    /** 最高分（默认 999999）. */
    private BigDecimal maxScore;

    /** 计分公式（可空，变量 actual/target/base/weight，支持 min/max）. */
    private String formula;

    /** 操作人（审计用，当前表无 created_by 列，由 scheme 侧记录）. */
    private String operator;
}
