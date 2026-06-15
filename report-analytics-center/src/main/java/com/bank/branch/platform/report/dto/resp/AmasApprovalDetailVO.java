package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 业绩分配审批历史详情视图（全部 VO，不暴露 entity）.
 *
 * <p>聚合三部分（只读）：
 * <ul>
 *   <li>{@code approval}：主表申请/审批信息（AMAS_PERF_ADJUST_APPROVAL）；</li>
 *   <li>{@code allocations}：业绩分配数据（AMAS_PERFORMANCE_ALLOCATION，按 PERF_ADJUST_NO 关联）；</li>
 *   <li>{@code apprRecords}：审批流程数据（AMAS_APPR_RECORD，REGION_DT_ID 关联，按序号倒序）。</li>
 * </ul>
 */
@Data
public class AmasApprovalDetailVO {

    /** 申请/审批主信息. */
    private AmasApprovalRowVO approval;

    /** 业绩分配明细. */
    private List<AmasAllocationVO> allocations;

    /** 审批流程流转记录（按序号倒序）. */
    private List<AmasApprRecordVO> apprRecords;
}
