package com.bank.branch.platform.report.dto.req;

import lombok.Data;

/**
 * 定价审批列表查询条件（页面上部查询项，全部可选）.
 *
 * <p>主查询项：客户名称、申请人姓名（均模糊）。分页由 common-web {@code PageRequest} 单独承载；
 * 列表固定按申请时间倒序（{@code APPLY_TIME DESC}）。数据范围按 BizType.REPORT 的数据范围标签控制
 * （本机构 / 本级及下级机构，基于 {@code APPLY_DEPTNO} 与机构表部门编号关联）。</p>
 */
@Data
public class AmasPriceApprovalQueryReqDTO {

    /** 客户名称（模糊匹配 CUST_NAME）. */
    private String custName;

    /** 申请人姓名（模糊匹配 APPLY_FULLNAME）. */
    private String applyFullname;

    /** 审批状态：0,待审批；1,通过；2,未通过（精确匹配，可选）. */
    private String apprStatus;

    /** 申请时间起（含，yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss，可选）. */
    private String applyTimeStart;

    /** 申请时间止（含，可选）. */
    private String applyTimeEnd;
}
