package com.bank.branch.platform.bizapp.listener;

import com.bank.branch.platform.bizapp.entity.LoanApply;
import com.bank.branch.platform.bizapp.enums.LoanStatus;
import com.bank.branch.platform.bizapp.event.LoanApprovedEvent;
import com.bank.branch.platform.bizapp.mapper.LoanApplyMapper;
import com.bank.branch.platform.workflow.listener.ProcessCompletedListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LoanWorkflowListener 单元测试（TDD）。
 * 4 个测试用例。
 */
@ExtendWith(MockitoExtension.class)
class LoanWorkflowListenerTest {

    @Mock
    private LoanApplyMapper loanMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private LoanWorkflowListener loanWorkflowListener;

    // ==================== 正常路径 ====================

    @Test
    void onProcessCompleted_loanBusinessKey_shouldUpdateStatusToCompleted() {
        // given: 业务键为 LOAN:xxx
        LoanApply loan = buildLoan("LOAN001");
        when(loanMapper.selectById("LOAN001")).thenReturn(loan);

        ProcessCompletedListener.ProcessCompletedEvent event =
                new ProcessCompletedListener.ProcessCompletedEvent("PI001", "LOAN:LOAN001");

        // when
        loanWorkflowListener.onProcessCompleted(event);

        // then: 状态更新为 COMPLETED
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(loanMapper).updateStatusById(anyString(), statusCaptor.capture(), anyString());
        assertThat(statusCaptor.getValue()).isEqualTo(LoanStatus.COMPLETED.getCode());
    }

    @Test
    void onProcessCompleted_nonLoanBusinessKey_shouldIgnore() {
        // given: 业务键不是 LOAN: 开头
        ProcessCompletedListener.ProcessCompletedEvent event =
                new ProcessCompletedListener.ProcessCompletedEvent("PI002", "LEAD:LEAD001");

        // when
        loanWorkflowListener.onProcessCompleted(event);

        // then: 不做任何操作
        verify(loanMapper, never()).selectById(any());
        verify(loanMapper, never()).updateStatusById(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void onProcessCompleted_loanNotFound_shouldLogWarnAndSkip() {
        // given: 贷款申请不存在
        when(loanMapper.selectById("NOTEXIST")).thenReturn(null);

        ProcessCompletedListener.ProcessCompletedEvent event =
                new ProcessCompletedListener.ProcessCompletedEvent("PI003", "LOAN:NOTEXIST");

        // when: 不抛出异常
        assertThatCode(() -> loanWorkflowListener.onProcessCompleted(event))
                .doesNotThrowAnyException();

        // then: 不更新状态，不发布事件
        verify(loanMapper, never()).updateStatusById(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void onProcessCompleted_shouldPublishLoanApprovedEvent() {
        // given
        LoanApply loan = buildLoan("LOAN001");
        when(loanMapper.selectById("LOAN001")).thenReturn(loan);

        ProcessCompletedListener.ProcessCompletedEvent event =
                new ProcessCompletedListener.ProcessCompletedEvent("PI004", "LOAN:LOAN001");

        // when
        loanWorkflowListener.onProcessCompleted(event);

        // then: 发布 LoanApprovedEvent
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(LoanApprovedEvent.class);
        LoanApprovedEvent approvedEvent = (LoanApprovedEvent) eventCaptor.getValue();
        assertThat(approvedEvent.getLoanId()).isEqualTo("LOAN001");
    }

    // ==================== 辅助方法 ====================

    private LoanApply buildLoan(String id) {
        LoanApply loan = new LoanApply();
        loan.setId(id);
        loan.setApplyNo("LA20260414000001");
        loan.setCustId("CUST001");
        loan.setStatus(LoanStatus.IN_APPROVAL.getCode());
        loan.setOwnerOrgId("ORG001");
        loan.setCreatedBy("E001");
        loan.setDeleted(0);
        return loan;
    }
}
