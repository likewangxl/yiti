package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.enums.BizAppErrorCode;
import com.bank.branch.platform.common.web.exception.BizException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 业务状态机服务。
 * <p>
 * 校验贷款申请（Loan）和支持请求（Support）的状态迁移合法性。
 * 无状态服务，不依赖任何 Mapper。
 * </p>
 */
@Service
public class BizStateMachine {

    /**
     * Loan 允许的状态迁移表。
     * key=当前状态, value=允许迁移到的目标状态集合。
     */
    private static final Map<String, Set<String>> LOAN_TRANSITIONS = new HashMap<>();

    /**
     * Support 允许的状态迁移表。
     * key=当前状态, value=允许迁移到的目标状态集合。
     */
    private static final Map<String, Set<String>> SUPPORT_TRANSITIONS = new HashMap<>();

    static {
        // Loan 状态迁移：DRAFT -> IN_APPROVAL -> COMPLETED/REJECTED/CANCELLED
        LOAN_TRANSITIONS.put("DRAFT", Set.of("IN_APPROVAL"));
        LOAN_TRANSITIONS.put("IN_APPROVAL", Set.of("COMPLETED", "REJECTED", "CANCELLED"));

        // Support 状态迁移：
        // DRAFT -> IN_APPROVAL（提交）
        // IN_APPROVAL -> IN_PROGRESS（派发，场景B）
        // IN_APPROVAL -> COMPLETED（完成，场景A）
        // IN_PROGRESS -> COMPLETED（完成，场景B）
        // IN_PROGRESS -> REJECTED（完成失败，场景B）
        // IN_APPROVAL -> CANCELLED（撤回）
        // IN_PROGRESS -> CANCELLED（撤回）
        SUPPORT_TRANSITIONS.put("DRAFT", Set.of("IN_APPROVAL"));
        SUPPORT_TRANSITIONS.put("IN_APPROVAL", Set.of("IN_PROGRESS", "COMPLETED", "REJECTED", "CANCELLED"));
        SUPPORT_TRANSITIONS.put("IN_PROGRESS", Set.of("COMPLETED", "REJECTED", "CANCELLED"));
    }

    /**
     * 校验 Loan 状态迁移是否合法。
     *
     * @param currentStatus 当前状态
     * @param targetStatus  目标状态
     * @throws BizException BIZ-42301 如果迁移不合法
     */
    public void validateLoanTransition(String currentStatus, String targetStatus) {
        validateTransition(currentStatus, targetStatus, LOAN_TRANSITIONS);
    }

    /**
     * 校验 Support 状态迁移是否合法。
     *
     * @param currentStatus 当前状态
     * @param targetStatus  目标状态
     * @throws BizException BIZ-42301 如果迁移不合法
     */
    public void validateSupportTransition(String currentStatus, String targetStatus) {
        validateTransition(currentStatus, targetStatus, SUPPORT_TRANSITIONS);
    }

    /**
     * 通用状态迁移校验。
     *
     * @param currentStatus 当前状态
     * @param targetStatus  目标状态
     * @param transitions   允许的迁移表
     */
    private void validateTransition(String currentStatus, String targetStatus,
                                     Map<String, Set<String>> transitions) {
        Set<String> allowed = transitions.get(currentStatus);
        // 当前状态不存在于迁移表，或目标状态不在允许集合中，均为非法迁移
        if (allowed == null || !allowed.contains(targetStatus)) {
            throw new BizException(
                    BizAppErrorCode.INVALID_STATUS_TRANSITION.getCode(),
                    BizAppErrorCode.INVALID_STATUS_TRANSITION.getMessage()
                    + ": " + currentStatus + " -> " + targetStatus
            );
        }
    }
}
