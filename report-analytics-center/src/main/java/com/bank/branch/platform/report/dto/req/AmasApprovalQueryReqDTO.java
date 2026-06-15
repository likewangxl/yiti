package com.bank.branch.platform.report.dto.req;

import lombok.Data;

/**
 * 业绩分配审批历史列表查询条件（页面上部查询项）.
 *
 * <p>全部为可选过滤项；分页由 common-web {@code PageRequest} 单独承载。
 * 列表固定按申请时间倒序（{@code APPLY_TIME DESC}）。</p>
 */
@Data
public class AmasApprovalQueryReqDTO {

    /** 业绩调整编号（模糊匹配）. */
    private String perfAdjustNo;

    /** 申请人工号（精确匹配）. */
    private String applyUsername;

    /** 客户关键词（匹配客户号或客户名称，模糊）. */
    private String custKeyword;

    /** 审批状态：0,待审批；1,已通过；2,已拒绝（精确匹配）. */
    private String apprStatus;

    /** 申请时间起（含，yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）. */
    private String applyTimeStart;

    /** 申请时间止（含）. */
    private String applyTimeEnd;
}
