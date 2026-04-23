package com.bank.branch.platform.performance.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分配关系调整申请 perf_alloc_adjust_apply 贫血实体 (V1.2 Q2).
 *
 * <p>字段严格对齐生产 DDL (docs/schema/ddl-performance.sql §15)：
 * <ul>
 *   <li>{@code id} varchar(32) 主键（申请 ID）</li>
 *   <li>{@code apply_no} 申请编号（UK）</li>
 *   <li>{@code cust_id} 客户 ID（单客户维度调整）</li>
 *   <li>{@code alloc_dim} 维度：RULE / ACCOUNT</li>
 *   <li>{@code biz_kind} 业务种类（用于区分对公/零售流程）</li>
 *   <li>{@code account_no} 账号（ACCOUNT 维度必填，可空）</li>
 *   <li>{@code status} 状态：DRAFT / IN_APPROVAL / APPROVED / REJECTED</li>
 *   <li>{@code business_key} 流程业务键</li>
 *   <li>{@code process_instance_id} Flowable 流程实例 ID</li>
 *   <li>{@code owner_org_id} 归属机构（数据范围过滤基准）</li>
 *   <li>{@code remark} 备注（text，可存结构化 JSON）</li>
 *   <li>审计：created_by / created_time / updated_by / updated_time</li>
 * </ul>
 *
 * <p>对公/零售路由：由 {@code biz_kind} 判定（如 CORP_LOAN → corp_v1，
 * RETAIL_CARD → retail_v1），生产 DDL 不存在 {@code adjust_type} 字段.
 */
@Data
public class PerfAllocAdjustApply {

    /** 申请 ID（varchar(32) 主键）. */
    private String id;

    /** 申请编号（UK，格式 AA{yyyyMMdd}{序号}）. */
    private String applyNo;

    /** 客户 ID（单客户维度调整）. */
    private String custId;

    /** 分配维度：RULE / ACCOUNT. */
    private String allocDim;

    /** 业务种类（决定流程路由）. */
    private String bizKind;

    /** 账号（ACCOUNT 维度必填，RULE 维度可空）. */
    private String accountNo;

    /** 状态：DRAFT / IN_APPROVAL / APPROVED / REJECTED. */
    private String status;

    /** 流程业务键（格式 ALLOC_ADJUST:{id}）. */
    private String businessKey;

    /** Flowable 流程实例 ID. */
    private String processInstanceId;

    /** 归属机构（数据范围过滤基准）. */
    private String ownerOrgId;

    /** 备注（text，可存申请原因或结构化 JSON 快照）. */
    private String remark;

    /** 创建人（申请人 empId）. */
    private String createdBy;

    /** 创建时间（DB 默认 CURRENT_TIMESTAMP）. */
    private LocalDateTime createdTime;

    /** 更新人. */
    private String updatedBy;

    /** 更新时间（DB ON UPDATE CURRENT_TIMESTAMP）. */
    private LocalDateTime updatedTime;
}
