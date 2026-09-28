package com.bank.branch.platform.performance.controller.dto;

import lombok.Data;

import java.util.List;

/**
 * KPI 计算结果详情（按对象分组）分页响应.
 *
 * <p>{@link #metrics} 为该 KPI 方案的指标列（按指标名排序），作为动态列定义；
 * {@link #records} 为当前页对象行（按对象分页）。
 */
@Data
public class KpiScoreGroupPageDTO {

    /** 指标列定义（该方案的所有指标，按指标名排序），前端据此动态渲染指标组列. */
    private List<MetricOptionDTO> metrics;

    /** 当前页对象行. */
    private List<KpiScoreGroupRowDTO> records;

    /** 对象总数（去重后的对象数）. */
    private long total;

    /** 页码. */
    private int pageNo;

    /** 每页条数. */
    private int pageSize;

    /**
     * 由后端确认并实际应用的机构查询范围；旧的未指定机构请求保持为 {@code null}。
     * 前端必须用该字段确认响应对应的支行范围。
     */
    private String scopeOrgCode;
}
