package com.bank.branch.platform.report.service;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.AllocAdjustApplyQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.AllocAdjustApplyDetailVO;
import com.bank.branch.platform.report.dto.resp.AllocAdjustApplyRowVO;

/**
 * 业绩调整（PERF_ALLOC_ADJUST_APPLY）查询服务（只读）.
 *
 * <p>列表按申请时间倒序分页；详情聚合分配明细（PERF_ALLOC_ADJUST_ITEM）。
 * 对外只暴露 VO，不暴露 entity（满足 report 架构守护）。</p>
 */
public interface AllocAdjustQueryService {

    /**
     * 分页查询业绩调整申请列表（申请时间倒序）.
     */
    PageResult<AllocAdjustApplyRowVO> pageList(AllocAdjustApplyQueryReqDTO req, PageRequest page);

    /**
     * 查询业绩调整申请详情（主信息 + 分配明细）.
     *
     * @throws com.bank.branch.platform.report.exception.RptException RPT-40012 申请不存在
     */
    AllocAdjustApplyDetailVO detail(String id);
}
