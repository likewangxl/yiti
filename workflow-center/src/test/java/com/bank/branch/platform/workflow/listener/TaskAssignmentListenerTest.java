package com.bank.branch.platform.workflow.listener;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.workflow.service.CandidateResolverService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
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

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private UserApi userApi;

    @Mock
    private com.bank.branch.platform.auth.api.OrgApi orgApi;

    @Mock
    private org.flowable.engine.TaskService taskService;

    @InjectMocks
    private TaskAssignmentListener taskAssignmentListener;

    /**
     * 复用工具：mock Repository 把 processDefinitionId → KEY 解析返回设定值。
     */
    private void stubProcDefKey(String processDefinitionId, String key) {
        ProcessDefinition pd = mock(ProcessDefinition.class);
        when(pd.getKey()).thenReturn(key);
        when(repositoryService.getProcessDefinition(processDefinitionId)).thenReturn(pd);
    }

    @Test
    void notify_resolvesCandidatesAndSetsOnTask() {
        // Arrange
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("loan_approve:1:123");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("userTask1");
        when(delegateTask.getId()).thenReturn("TASK_001");
        stubProcDefKey("loan_approve:1:123", "loan_approve");

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
        stubProcDefKey("loan_approve:2:456", "loan_approve");

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
        stubProcDefKey("loan_approve:1:123", "loan_approve");

        when(candidateResolverService.resolveCandidates("loan_approve", "userTask1"))
                .thenReturn(Collections.emptyList());

        // Act
        taskAssignmentListener.notify(delegateTask);

        // Assert
        verify(delegateTask, never()).addCandidateGroup(anyString());
    }

    @Test
    void notify_originalOwnerVirtual_autoApprovesWithDefaultAgree() {
        // Arrange：original_owner_approve 节点，原业绩所属人为虚拟员工(userType=2)
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("perf_alloc_adjust_corp_v1:1:1");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("original_owner_approve");
        when(delegateTask.getId()).thenReturn("TASK_OWNER_V");
        when(delegateTask.getAssignee()).thenReturn("U_VT");
        when(delegateTask.getProcessInstanceId()).thenReturn("PROC_1");
        com.bank.branch.platform.auth.api.dto.UserDTO owner = new com.bank.branch.platform.auth.api.dto.UserDTO();
        owner.setUserType("2");
        when(userApi.getUserByEmpId("U_VT")).thenReturn(owner);

        // Act
        taskAssignmentListener.notify(delegateTask);

        // Assert：自动「默认同意」并完成任务，不再走候选组解析
        verify(taskService).addComment("TASK_OWNER_V", "PROC_1", "APPROVE", "默认同意");
        verify(taskService).complete("TASK_OWNER_V", java.util.Map.of("approved", true));
        verify(candidateResolverService, never()).resolveCandidates(anyString(), anyString());
    }

    @Test
    void notify_originalOwnerNotVirtual_goesManual() {
        // Arrange：original_owner_approve 节点，原业绩所属人为普通员工(userType=1)
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("perf_alloc_adjust_corp_v1:1:2");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("original_owner_approve");
        when(delegateTask.getId()).thenReturn("TASK_OWNER_R");
        when(delegateTask.getAssignee()).thenReturn("U_REAL");
        stubProcDefKey("perf_alloc_adjust_corp_v1:1:2", "perf_alloc_adjust_corp_v1");
        com.bank.branch.platform.auth.api.dto.UserDTO owner = new com.bank.branch.platform.auth.api.dto.UserDTO();
        owner.setUserType("1");
        when(userApi.getUserByEmpId("U_REAL")).thenReturn(owner);
        when(candidateResolverService.resolveCandidates("perf_alloc_adjust_corp_v1", "original_owner_approve"))
                .thenReturn(Collections.emptyList());

        // Act
        taskAssignmentListener.notify(delegateTask);

        // Assert：普通员工不自动完成任务
        verify(taskService, never()).complete(anyString(), anyMap());
    }

    @Test
    void notify_todoNotificationAlwaysSuppressed() {
        // Arrange
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("loan_approve:1:123");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("userTask1");
        when(delegateTask.getId()).thenReturn("TASK_004");
        stubProcDefKey("loan_approve:1:123", "loan_approve");

        when(candidateResolverService.resolveCandidates("loan_approve", "userTask1"))
                .thenReturn(List.of("ROLE:CUST_MANAGER"));

        // Act - 候选组照常设置，待审批"待办"通知统一抑制，不调用通知 API
        taskAssignmentListener.notify(delegateTask);

        // Assert
        verify(delegateTask).addCandidateGroup("ROLE:CUST_MANAGER");
        verify(notifyApi, never()).batchSendNotifications(anyList());
    }

    @Test
    void notify_roleCandidate_expandsToEmpIdsAndNotifiesEach() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("alloc_approve_v1:1:abc");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("branch_approve");
        when(delegateTask.getId()).thenReturn("TASK_ROLE_001");
        stubProcDefKey("alloc_approve_v1:1:abc", "alloc_adjust_approve_v1");

        when(candidateResolverService.resolveCandidates("alloc_adjust_approve_v1", "branch_approve"))
                .thenReturn(List.of("ROLE:BRANCH_HEAD"));
        when(userApi.getEmpIdsByRoleCode("BRANCH_HEAD")).thenReturn(List.of("E10001", "E10002"));

        taskAssignmentListener.notify(delegateTask);

        // 候选组照常设置，待审批通知统一抑制
        verify(delegateTask).addCandidateGroup("ROLE:BRANCH_HEAD");
        verify(notifyApi, never()).batchSendNotifications(anyList());
    }

    @Test
    void notify_userCandidate_passesEmpIdDirectly() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("alloc_approve_v1:1:abc");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("branch_approve");
        when(delegateTask.getId()).thenReturn("TASK_USER_001");
        stubProcDefKey("alloc_approve_v1:1:abc", "alloc_adjust_approve_v1");

        when(candidateResolverService.resolveCandidates("alloc_adjust_approve_v1", "branch_approve"))
                .thenReturn(List.of("USER:E20001"));

        taskAssignmentListener.notify(delegateTask);

        verify(delegateTask).addCandidateGroup("USER:E20001");
        verify(notifyApi, never()).batchSendNotifications(anyList());
    }

    /**
     * Flowable 7 默认 processDefinitionId 为 UUID（无 ":" 分隔）。
     * listener 必须通过 RepositoryService 反查真实 KEY，不能依赖 split(":")[0]。
     */
    @Test
    void notify_uuidProcessDefinitionId_resolvesKeyViaRepositoryService() {
        // Arrange — Flowable 7 UUID 格式（无 ":" ）
        String uuidPdId = "ea266b69-4aad-11f1-9209-029316f18a46";
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn(uuidPdId);
        when(delegateTask.getTaskDefinitionKey()).thenReturn("branch_mgr_review");
        when(delegateTask.getId()).thenReturn("TASK_UUID_001");
        stubProcDefKey(uuidPdId, "perf_alloc_adjust_corp_v1");

        when(candidateResolverService.resolveCandidates("perf_alloc_adjust_corp_v1", "branch_mgr_review"))
                .thenReturn(List.of("E20001"));

        // Act
        taskAssignmentListener.notify(delegateTask);

        // Assert — 候选人按真实 BPMN KEY 解析后落到任务
        verify(delegateTask).addCandidateGroup("E20001");
    }
}
