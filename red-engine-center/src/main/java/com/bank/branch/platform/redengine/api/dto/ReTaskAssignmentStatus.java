package com.bank.branch.platform.redengine.api.dto;

/** 党支部任务分配状态。 */
public enum ReTaskAssignmentStatus {

    /** 窗口结束且完全未上报。 */
    UNREPORTED,
    /** 报送员已提交，等待支部书记审核。 */
    BRANCH_PENDING,
    /** 支部已提交至组织审核员。 */
    ORG_PENDING,
    /** 组织审核通过。 */
    APPROVED,
    /** 支部书记驳回。 */
    REJECTED_BY_BRANCH,
    /** 组织审核员驳回。 */
    REJECTED_BY_ORG,
    /** 分配窗口已关闭。 */
    CLOSED
}
