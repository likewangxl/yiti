package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BizStateMachine 单元测试（TDD）。
 * 共11个测试用例：4个合法Loan迁移 + 4个合法Support迁移 + 3个非法迁移。
 */
@ExtendWith(MockitoExtension.class)
class BizStateMachineTest {

    @InjectMocks
    private BizStateMachine bizStateMachine;

    // ==================== Loan 合法迁移 ====================

    @Test
    void validateLoanTransition_DRAFT_to_IN_APPROVAL_shouldPass() {
        assertThatCode(() -> bizStateMachine.validateLoanTransition("DRAFT", "IN_APPROVAL"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateLoanTransition_IN_APPROVAL_to_COMPLETED_shouldPass() {
        assertThatCode(() -> bizStateMachine.validateLoanTransition("IN_APPROVAL", "COMPLETED"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateLoanTransition_IN_APPROVAL_to_REJECTED_shouldPass() {
        assertThatCode(() -> bizStateMachine.validateLoanTransition("IN_APPROVAL", "REJECTED"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateLoanTransition_IN_APPROVAL_to_CANCELLED_shouldPass() {
        assertThatCode(() -> bizStateMachine.validateLoanTransition("IN_APPROVAL", "CANCELLED"))
                .doesNotThrowAnyException();
    }

    // ==================== Loan 非法迁移 ====================

    @Test
    void validateLoanTransition_DRAFT_to_COMPLETED_shouldThrow() {
        assertThatThrownBy(() -> bizStateMachine.validateLoanTransition("DRAFT", "COMPLETED"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-42301");
    }

    @Test
    void validateLoanTransition_COMPLETED_to_any_shouldThrow() {
        assertThatThrownBy(() -> bizStateMachine.validateLoanTransition("COMPLETED", "DRAFT"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-42301");
    }

    // ==================== Support 合法迁移 ====================

    @Test
    void validateSupportTransition_IN_APPROVAL_to_IN_PROGRESS_shouldPass() {
        assertThatCode(() -> bizStateMachine.validateSupportTransition("IN_APPROVAL", "IN_PROGRESS"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateSupportTransition_IN_APPROVAL_to_COMPLETED_shouldPass() {
        assertThatCode(() -> bizStateMachine.validateSupportTransition("IN_APPROVAL", "COMPLETED"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateSupportTransition_IN_PROGRESS_to_COMPLETED_shouldPass() {
        assertThatCode(() -> bizStateMachine.validateSupportTransition("IN_PROGRESS", "COMPLETED"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateSupportTransition_IN_PROGRESS_to_REJECTED_shouldPass() {
        assertThatCode(() -> bizStateMachine.validateSupportTransition("IN_PROGRESS", "REJECTED"))
                .doesNotThrowAnyException();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Support IN_APPROVAL → REJECTED 应合法")
    void supportTransition_inApproval_rejected_allowed() {
        assertThatCode(() -> bizStateMachine.validateSupportTransition("IN_APPROVAL", "REJECTED"))
                .doesNotThrowAnyException();
    }

    // ==================== Support 非法迁移 ====================

    @Test
    void validateSupportTransition_DRAFT_to_COMPLETED_shouldThrow() {
        assertThatThrownBy(() -> bizStateMachine.validateSupportTransition("DRAFT", "COMPLETED"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-42301");
    }
}
