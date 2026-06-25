package com.bank.branch.platform.report.service;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.AmasPriceApprovalQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.AmasPriceApprovalVO;

/**
 * 价格审批查询服务（只读）：列表（分页 + 过滤 + 数据范围）与详情.
 */
public interface AmasPriceApprovalQueryService {

    /**
     * 价格审批列表（申请时间倒序，顶部查询项过滤，应用 BizType.REPORT 数据范围）.
     *
     * @param req  查询条件（可空）
     * @param page 分页参数
     * @return 分页结果（VO）
     */
    PageResult<AmasPriceApprovalVO> pageList(AmasPriceApprovalQueryReqDTO req, PageRequest page);

    /**
     * 价格审批详情.
     *
     * @param priceApprId 价格审批编号
     * @return 详情 VO
     */
    AmasPriceApprovalVO detail(String priceApprId);
}
