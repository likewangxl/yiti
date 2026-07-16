package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramNodeDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PerfAllocOverdueNotifyJob 单测：分配调整审批超时（满14天）提醒。
 */
@ExtendWith(MockitoExtension.class)
class PerfAllocOverdueNotifyJobTest {

    @Mock
    private PerfAllocAdjustApplyMapper applyMapper;
    @Mock
    private NotifyApi notifyApi;
    @Mock
    private WorkflowQueryApi workflowQueryApi;

    private PerfAllocOverdueNotifyJob job;

    private final LocalDateTime now = LocalDateTime.of(2026, 7, 16, 9, 0);

    @BeforeEach
    void setUp() {
        job = new PerfAllocOverdueNotifyJob(applyMapper, notifyApi, workflowQueryApi);
    }

    private PerfAllocAdjustApply overdueApply(LocalDateTime createdTime, String pid) {
        PerfAllocAdjustApply a = new PerfAllocAdjustApply();
        a.setId("A1");
        a.setApplyNo("AA001");
        a.setCustName("张三");
        a.setCreatedBy("E1");
        a.setStatus("IN_APPROVAL");
        a.setCreatedTime(createdTime);
        a.setProcessInstanceId(pid);
        return a;
    }

    private ProcessDiagramDTO diagramWithActive(String nodeName) {
        ProcessDiagramNodeDTO n = new ProcessDiagramNodeDTO();
        n.setStatus("ACTIVE");
        n.setNodeType("userTask");
        n.setNodeName(nodeName);
        n.setNodeKey("branch_approve");
        ProcessDiagramDTO d = new ProcessDiagramDTO();
        d.setNodes(List.of(n));
        return d;
    }

    @Test
    void overdue_sendsNotification_withNodeAndDays_andMarksNotified() {
        PerfAllocAdjustApply a = overdueApply(now.minusDays(15), "PID1");
        when(applyMapper.selectList(any())).thenReturn(List.of(a));
        when(workflowQueryApi.getProcessNodes("PID1")).thenReturn(diagramWithActive("机构负责人审批"));

        job.run(now);

        ArgumentCaptor<NotificationCmd> cap = ArgumentCaptor.forClass(NotificationCmd.class);
        verify(notifyApi).sendNotification(cap.capture());
        NotificationCmd cmd = cap.getValue();
        assertThat(cmd.getTargetEmpId()).isEqualTo("E1");
        assertThat(cmd.getTitle()).isEqualTo("业绩分配调整审批超时提醒");
        assertThat(cmd.getContent()).contains("15 天").contains("机构负责人审批").contains("AA001").contains("张三");
        assertThat(cmd.getBizType()).isEqualTo("ALLOC_ADJUST");
        assertThat(cmd.getBizId()).isEqualTo("A1");
        // 只提醒一次：置 overdue_notified_time（updateById 仅更新非空字段）
        ArgumentCaptor<PerfAllocAdjustApply> patchCap = ArgumentCaptor.forClass(PerfAllocAdjustApply.class);
        verify(applyMapper).updateById(patchCap.capture());
        assertThat(patchCap.getValue().getId()).isEqualTo("A1");
        assertThat(patchCap.getValue().getOverdueNotifiedTime()).isEqualTo(now);
    }

    @Test
    void noActiveNode_contentFallsBackToApprovingText() {
        PerfAllocAdjustApply a = overdueApply(now.minusDays(20), "PID1");
        when(applyMapper.selectList(any())).thenReturn(List.of(a));
        when(workflowQueryApi.getProcessNodes("PID1")).thenReturn(null);

        job.run(now);

        ArgumentCaptor<NotificationCmd> cap = ArgumentCaptor.forClass(NotificationCmd.class);
        verify(notifyApi).sendNotification(cap.capture());
        assertThat(cap.getValue().getContent()).contains("审批中").contains("20 天");
    }

    @Test
    void sendFails_doesNotMark_andDoesNotThrow() {
        PerfAllocAdjustApply a = overdueApply(now.minusDays(15), "PID1");
        when(applyMapper.selectList(any())).thenReturn(List.of(a));
        when(workflowQueryApi.getProcessNodes("PID1")).thenReturn(diagramWithActive("机构负责人审批"));
        doThrow(new RuntimeException("boom")).when(notifyApi).sendNotification(any());

        job.run(now); // 不抛出

        verify(applyMapper, never()).updateById(any(PerfAllocAdjustApply.class));
    }

    @Test
    void noOverdue_emptyList_noNotification() {
        when(applyMapper.selectList(any())).thenReturn(List.of());

        job.run(now);

        verify(notifyApi, never()).sendNotification(any());
    }

    @Test
    void exactly14Days_countedAs14() {
        PerfAllocAdjustApply a = overdueApply(now.minusDays(14), "PID1");
        when(applyMapper.selectList(any())).thenReturn(List.of(a));
        when(workflowQueryApi.getProcessNodes("PID1")).thenReturn(diagramWithActive("机构负责人审批"));

        job.run(now);

        ArgumentCaptor<NotificationCmd> cap = ArgumentCaptor.forClass(NotificationCmd.class);
        verify(notifyApi).sendNotification(cap.capture());
        assertThat(cap.getValue().getContent()).contains("14 天");
    }
}
