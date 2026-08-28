package com.bank.branch.platform.customer.listener;

import com.bank.branch.platform.customer.entity.AssetProjectUrgentApply;
import com.bank.branch.platform.customer.mapper.AssetProjectApplyMapper;
import com.bank.branch.platform.customer.mapper.AssetProjectUrgentApplyMapper;
import com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetProjectWorkflowListenerTest {
    @Mock private AssetProjectApplyMapper applyMapper;
    @Mock private AssetProjectUrgentApplyMapper urgentMapper;
    @InjectMocks private AssetProjectWorkflowListener listener;

    @Test
    void approvedMain_shouldCompleteOnlyAnInApprovalProject() {
        listener.onProcessCompleted(new ProcessCompletedEvent(
                "PI-1", "ASSET_PROJECT:9001", "APPROVED", null));

        verify(applyMapper).conditionalUpdateStatus(eq(9001L), eq("IN_APPROVAL"),
                eq("COMPLETED"), eq("SYSTEM"), any());
    }

    @Test
    void rejectedMain_shouldRejectOnlyAnInApprovalProject() {
        listener.onProcessCompleted(new ProcessCompletedEvent(
                "PI-1", "ASSET_PROJECT:9001", "REJECTED", "资料不全"));

        verify(applyMapper).conditionalUpdateStatus(eq(9001L), eq("IN_APPROVAL"),
                eq("REJECTED"), eq("SYSTEM"), any());
    }

    @Test
    void unknownMainOutcome_shouldFailCloseWithoutChangingProject() {
        listener.onProcessCompleted(new ProcessCompletedEvent(
                "PI-1", "ASSET_PROJECT:9001", "CANCELLED", "未知终态"));

        verify(applyMapper, never()).conditionalUpdateStatus(any(), any(), any(), any(), any());
    }

    @Test
    void approvedUrgent_shouldCompleteChildThenMarkMainUrgent() {
        AssetProjectUrgentApply urgent = new AssetProjectUrgentApply();
        urgent.setId(71L);
        urgent.setAssetProjectApplyId(9001L);
        when(urgentMapper.selectById(71L)).thenReturn(urgent);
        when(urgentMapper.complete(eq(71L), eq("IN_APPROVAL"), eq("APPROVED"),
                eq("SYSTEM"), eq("同意"), any())).thenReturn(1);

        listener.onProcessCompleted(new ProcessCompletedEvent(
                "PI-U1", "ASSET_PROJECT_URGENT:71", "APPROVED", "同意"));

        verify(applyMapper).markUrgentApproved(eq(9001L), eq("SYSTEM"), any());
    }

    @Test
    void duplicateApprovedUrgentCallback_shouldNotMarkMainAgain() {
        AssetProjectUrgentApply urgent = new AssetProjectUrgentApply();
        urgent.setId(71L);
        urgent.setAssetProjectApplyId(9001L);
        when(urgentMapper.selectById(71L)).thenReturn(urgent);
        when(urgentMapper.complete(eq(71L), eq("IN_APPROVAL"), eq("APPROVED"),
                eq("SYSTEM"), any(), any())).thenReturn(0);

        listener.onProcessCompleted(new ProcessCompletedEvent(
                "PI-U1", "ASSET_PROJECT_URGENT:71", "APPROVED", null));

        verify(applyMapper, never()).markUrgentApproved(any(), any(), any());
    }

    @Test
    void unknownUrgentOutcome_shouldFailCloseWithoutChangingChildOrMain() {
        listener.onProcessCompleted(new ProcessCompletedEvent(
                "PI-U1", "ASSET_PROJECT_URGENT:71", "CANCELLED", null));

        verify(urgentMapper, never()).selectById(any());
        verify(urgentMapper, never()).complete(any(), any(), any(), any(), any(), any());
        verify(applyMapper, never()).markUrgentApproved(any(), any(), any());
    }
}
