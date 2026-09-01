package com.bank.branch.platform.bizapp.api.dto;

import lombok.Data;

/**
 * 客户正在运行的业务申请数量汇总 DTO。
 * <p>
 * 用于 {@code BizApplyQueryApi.countRunningApplications()} 的返回结果。
 * 聚合贷款申请和中台支持申请两个域的运行中数量。
 * </p>
 */
@Data
public class RunningAppCountDTO {

    /** 正在运行的贷款申请数量（IN_APPROVAL 状态） */
    private long runningLoanCount;

    /** 正在运行的支持申请数量（IN_APPROVAL/IN_PROGRESS 状态） */
    private long runningSupportCount;
}
