package com.bank.branch.platform.performance.eval.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 评价人组请求体。
 */
@Data
public class GroupReq {
    /** 组类型：1=按标签选人, 2=部门员工组. */
    @NotNull
    private Integer groupType;
    /** type=1 时的评价人标签ID（type=2 时可为 null）. */
    private Long evalTagId;
    /** 权重百分比（如 60.00），所有组之和必须 = 100.00. */
    @NotNull
    private BigDecimal weight;
    /** 排序序号. */
    private Integer sortOrder;
    /** 评分方式：1=数值打分（默认）, 2=等级打分. */
    private Integer scoreMode;
}
