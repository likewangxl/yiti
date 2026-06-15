package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 业绩调整（PERF_ALLOC_ADJUST_APPLY）详情视图（全部 VO，不暴露 entity）.
 *
 * <p>聚合两部分（只读）：
 * <ul>
 *   <li>{@code apply}：申请主信息（PERF_ALLOC_ADJUST_APPLY）；</li>
 *   <li>{@code items}：业绩分配数据（PERF_ALLOC_ADJUST_ITEM，按 apply_id 关联）。</li>
 * </ul>
 * 注：平台流程的审批流转记录走 Flowable（业务键 business_key），本期详情暂不含审批流。</p>
 */
@Data
public class AllocAdjustApplyDetailVO {

    /** 申请主信息. */
    private AllocAdjustApplyRowVO apply;

    /** 业绩分配明细. */
    private List<AllocAdjustItemVO> items;
}
