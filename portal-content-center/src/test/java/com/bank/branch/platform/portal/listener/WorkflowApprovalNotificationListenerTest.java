package com.bank.branch.platform.portal.listener;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import com.bank.branch.platform.governance.service.NotificationService;
import com.bank.branch.platform.workflow.api.event.ProcessWithdrawnEvent;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import com.bank.branch.platform.workflow.service.TaskOperationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowApprovalNotificationListenerTest {

    @Mock BizProcessMapMapper bizProcessMapMapper;
    @Mock UserApi userApi;
    @Mock NotificationService notificationService;

    @InjectMocks WorkflowApprovalNotificationListener listener;

    @Test
    void n1_onTaskApproved_sendsToApplicant() {
        BizProcessMap map = new BizProcessMap();
        map.setStartUser("E10001");
        map.setBizType("ALLOC_ADJUST");
        map.setBizId("A1");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_001")).thenReturn(map);
        when(userApi.getUserName("E20001")).thenReturn("zhangsan");

        listener.onTaskApproved(new TaskOperationService.TaskApprovedEvent("TASK_1", "PID_001", "E20001"));

        ArgumentCaptor<NotificationCmd> captor = ArgumentCaptor.forClass(NotificationCmd.class);
        verify(notificationService).sendNotification(captor.capture());
        NotificationCmd cmd = captor.getValue();
        assertThat(cmd.getTargetEmpId()).isEqualTo("E10001");
        assertThat(cmd.getContent()).isEqualTo("您的【业绩调整审批】申请已通过审批（审批人：zhangsan）");
        assertThat(cmd.getBizType()).isEqualTo("ALLOC_ADJUST");
        assertThat(cmd.getBizId()).isEqualTo("A1");
    }

    @Test
    void n2_onTaskRejected_sendsToApplicant() {
        BizProcessMap map = new BizProcessMap();
        map.setStartUser("E10001");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_001")).thenReturn(map);
        when(userApi.getUserName("E20001")).thenReturn("lisi");

        listener.onTaskRejected(new TaskOperationService.TaskRejectedEvent("TASK_1", "PID_001", "E20001"));

        ArgumentCaptor<NotificationCmd> captor = ArgumentCaptor.forClass(NotificationCmd.class);
        verify(notificationService).sendNotification(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("您的【流程】申请被驳回（审批人：lisi）");
    }

    @Test
    void n3_onProcessWithdrawn_sendsToAssignee() {
        when(userApi.getUserName("E10001")).thenReturn("wangwu");
        // BizProcessMap 可有可无（bizType/bizId 仅辅助），mock 返 null 走兼容分支
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_001")).thenReturn(null);

        listener.onProcessWithdrawn(new ProcessWithdrawnEvent(
                "PID_001", "ALLOC_ADJUST:A1", "E10001", "E20001", "改主意"));

        ArgumentCaptor<NotificationCmd> captor = ArgumentCaptor.forClass(NotificationCmd.class);
        verify(notificationService).sendNotification(captor.capture());
        NotificationCmd cmd = captor.getValue();
        assertThat(cmd.getTargetEmpId()).isEqualTo("E20001");
        assertThat(cmd.getContent()).isEqualTo("【流程】申请人 wangwu 已撤回申请");
    }

    @Test
    void n4_onProcessWithdrawn_nullAssignee_skips() {
        listener.onProcessWithdrawn(new ProcessWithdrawnEvent(
                "PID_001", "ALLOC_ADJUST:A1", "E10001", null, "改主意"));

        verify(notificationService, never()).sendNotification(org.mockito.ArgumentMatchers.any());
    }
}
