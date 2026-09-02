package com.bank.branch.platform.redengine.api.dto;

/**
 * 支部任务提交状态。
 * <p>支部“通过”和“提交至组织审核”是两个独立动作，因此
 * {@link #BRANCH_APPROVED} 不能直接跳转到 {@link #ORG_PENDING} 之外的状态。</p>
 */
public enum ReTaskSubmissionStatus {

    /** 尚未提交的草稿，数据库约束允许该状态。 */
    DRAFT,
    /** 等待支部书记审核。 */
    BRANCH_PENDING,
    /** 支部书记已通过，等待其单独提交至组织审核。 */
    BRANCH_APPROVED,
    /** 等待组织审核员审核。 */
    ORG_PENDING,
    /** 组织审核通过。 */
    APPROVED,
    /** 支部书记驳回，回到报送员。 */
    REJECTED_BY_BRANCH,
    /** 组织审核员驳回，回到报送员。 */
    REJECTED_BY_ORG;

    /**
     * 判断状态转换是否符合任务审核状态机。
     *
     * @param target 目标状态
     * @return 允许转换返回 true，否则返回 false
     */
    public boolean canTransitionTo(ReTaskSubmissionStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case DRAFT -> target == BRANCH_PENDING;
            case BRANCH_PENDING -> target == BRANCH_APPROVED || target == REJECTED_BY_BRANCH;
            case BRANCH_APPROVED -> target == ORG_PENDING;
            case ORG_PENDING -> target == APPROVED || target == REJECTED_BY_ORG;
            case REJECTED_BY_BRANCH, REJECTED_BY_ORG -> target == BRANCH_PENDING;
            case APPROVED -> false;
        };
    }
}
