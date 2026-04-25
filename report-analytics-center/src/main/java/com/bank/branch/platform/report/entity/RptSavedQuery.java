package com.bank.branch.platform.report.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * rpt_saved_query 实体 —— 动态查询保存方案.
 *
 * <p>贫血模型，字段完全对齐 {@code V1_0_0__rpt_init.sql} §1.
 *
 * <p>业务约束：
 * <ul>
 *   <li>每用户最多 10 条方案（应用层 {@link com.bank.branch.platform.report.mapper.RptSavedQueryMapper#countByEmpId} 判定）</li>
 *   <li>subjectIds / metricCodes 是 JSON 数组字符串（text 类型存储）</li>
 *   <li>version 用于乐观锁（多标签页并发编辑冲突保护）</li>
 * </ul>
 */
@Data
public class RptSavedQuery {

    /** 方案 ID（UUID） */
    private String id;

    /** 员工工号 */
    private String empId;

    /** 方案名称 */
    private String name;

    /** 维度：EMP / ORG / CUST */
    private String dim;

    /** 对象 ID 列表（JSON 数组字符串） */
    private String subjectIds;

    /** 指标编码列表（JSON 数组字符串） */
    private String metricCodes;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
