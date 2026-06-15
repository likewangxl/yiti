package com.bank.branch.platform.report.service;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.AmasApprovalQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.AmasApprovalDetailVO;
import com.bank.branch.platform.report.dto.resp.AmasApprovalRowVO;

/**
 * 业绩分配审批历史查询服务（只读）.
 *
 * <p>以 {@code AMAS_PERF_ADJUST_APPROVAL} 为主表：列表按申请时间倒序分页；
 * 详情聚合分配明细（AMAS_PERFORMANCE_ALLOCATION）与审批流程（AMAS_APPR_RECORD）。
 * 对外只暴露 VO，不暴露 entity（满足 report 架构守护）。</p>
 */
public interface AmasApprovalQueryService {

    /**
     * 分页查询业绩分配审批历史列表（申请时间倒序）.
     *
     * @param req  查询条件（全部可选）
     * @param page 分页参数
     * @return 分页结果（行视图）
     */
    PageResult<AmasApprovalRowVO> pageList(AmasApprovalQueryReqDTO req, PageRequest page);

    /**
     * 查询业绩分配审批历史详情.
     *
     * @param perfAdjustNo 业绩调整编号
     * @return 详情视图（主信息 + 分配明细 + 审批流程）
     * @throws com.bank.branch.platform.report.exception.RptException RPT-40011 记录不存在
     */
    AmasApprovalDetailVO detail(String perfAdjustNo);
}
