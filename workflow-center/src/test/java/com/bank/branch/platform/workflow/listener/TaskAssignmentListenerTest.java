package com.bank.branch.platform.workflow.listener;

import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.workflow.service.CandidateResolverService;
import org.flowable.task.service.delegate.DelegateTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * TaskAssignmentListener 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TaskAssignmentListenerTest {

    @Mock
    private CandidateResolverService candidateResolverService;

    @Mock
    private NotifyApi notifyApi;

    @InjectMocks
    private TaskAssignmentListener taskAssignmentListener;

    @Test
    void notify_resolvesCandidatesAndSetsOnTask() {
        // Arrange
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("loan_approve:1:123");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("userTask1");
        when(delegateTask.getId()).thenReturn("TASK_001");

        when(candidateResolverService.resolveCandidates("loan_approve", "userTask1"))
                .thenReturn(List.of("ROLE:CUST_MANAGER"));

        // Act
        taskAssignmentListener.notify(delegateTask);

        // Assert
        verify(delegateTask).addCandidateGroup("ROLE:CUST_MANAGER");
    }

    @Test
    void notify_multipleCandidates_setsAllOnTask() {
        // Arrange
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("loan_approve:2:456");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("userTask2");
        when(delegateTask.getId()).thenReturn("TASK_002");

        when(candidateResolverService.resolveCandidates("loan_approve", "userTask2"))
                .thenReturn(List.of("ROLE:CUST_MANAGER", "ORG:BRANCH_001"));

        // Act
        taskAssignmentListener.notify(delegateTask);

        // Assert
        verify(delegateTask).addCandidateGroup("ROLE:CUST_MANAGER");
        verify(delegateTask).addCandidateGroup("ORG:BRANCH_001");
    }

    @Test
    void notify_noCandidates_noGroupsAdded() {
        // Arrange
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("loan_approve:1:123");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("userTask1");
        when(delegateTask.getId()).thenReturn("TASK_003");

        when(candidateResolverService.resolveCandidates("loan_approve", "userTask1"))
                .thenReturn(Collections.emptyList());

        // Act
        taskAssignmentListener.notify(delegateTask);

        // Assert
        verify(delegateTask, never()).addCandidateGroup(anyString());
    }

    @Test
    void notify_notifyApiThrows_doesNotCrash() {
        // Arrange
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("loan_approve:1:123");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("userTask1");
        when(delegateTask.getId()).thenReturn("TASK_004");

        when(candidateResolverService.resolveCandidates("loan_approve", "userTask1"))
                .thenReturn(List.of("ROLE:CUST_MANAGER"));

        doThrow(new RuntimeException("notify failed"))
                .when(notifyApi).batchSendNotifications(anyList());

        // Act - should not throw
        taskAssignmentListener.notify(delegateTask);

        // Assert - candidate group should still have been set
        verify(delegateTask).addCandidateGroup("ROLE:CUST_MANAGER");
    }
}
