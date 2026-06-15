package com.bank.branch.platform.report.dto.req;

import lombok.Data;

/**
 * 业绩调整（PERF_ALLOC_ADJUST_APPLY）列表查询条件.
 *
 * <p>全部可选过滤项；分页由 common-web {@code PageRequest} 单独承载，列表固定按
 * 申请时间倒序（{@code created_time DESC}）。</p>
 */
@Data
public class AllocAdjustApplyQueryReqDTO {

    /** 申请人工号（created_by，精确匹配）. */
    private String applicant;

    /** 客户关键词（匹配客户号或客户名称，模糊）. */
    private String custKeyword;

    /** 状态：IN_APPROVAL/APPROVED/REJECTED/WITHDRAWN/DRAFT（精确匹配）. */
    private String status;

    /** 申请时间起（含，yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）. */
    private String createdStart;

    /** 申请时间止（含）. */
    private String createdEnd;
}
