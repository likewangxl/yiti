package com.bank.branch.platform.performance.service.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 更新 KPI 方案项命令.
 *
 * <p>选择性更新: 任何字段为 null 表示不修改, 非 null 走 MyBatis
 * {@code updateByIdSelective} 的 {@code <if>} 分支写入.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateKpiItemCmd {

    /** 权重. */
    private BigDecimal weight;

    /** 加倍系数. */
    private BigDecimal multiplier;

    /** 最低分. */
    private BigDecimal minScore;

    /** 最高分. */
    private BigDecimal maxScore;

    /** 操作人（审计用）. */
    private String operator;
}
