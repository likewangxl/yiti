package com.bank.branch.platform.workflow.listener;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.workflow.api.dto.ApproverGroupDTO;
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
import java.util.Map;

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

    @Mock
    private com.bank.branch.platform.workflow.mapper.BizProcessMapMapper bizProcessMapMapper;

    @Mock
    private com.bank.branch.platform.workflow.service.WfProcessOrgService wfProcessOrgService;

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
     * 虚拟员工默认通过（泛化）：任意审批节点（非 original_owner_approve），
     * 受理人为虚拟员工(userType=2) → 自动「默认同意」并完成任务，不走候选解析。
     */
    @Test
    void notify_anyNodeVirtualAssignee_autoApproves() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc:1:9");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("approval_x");   // 非 original_owner_approve
        when(delegateTask.getId()).thenReturn("TASK_VT_X");
        when(delegateTask.getAssignee()).thenReturn("U_VT2");
        when(delegateTask.getProcessInstanceId()).thenReturn("PROC_X");
        com.bank.branch.platform.auth.api.dto.UserDTO owner = new com.bank.branch.platform.auth.api.dto.UserDTO();
        owner.setUserType("2");
        when(userApi.getUserByEmpId("U_VT2")).thenReturn(owner);

        taskAssignmentListener.notify(delegateTask);

        verify(taskService).addComment("TASK_VT_X", "PROC_X", "APPROVE", "默认同意");
        verify(taskService).complete("TASK_VT_X", java.util.Map.of("approved", true));
        verify(candidateResolverService, never()).resolveCandidates(anyString(), anyString());
    }

    /**
     * 审批机构归属 SELF（本机构）：按发起人机构号过滤候选，ROLE 候选展开为该机构员工候选用户。
     */
    @Test
    void notify_approveOrgScopeSelf_filtersByStartOrg() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc:1:1");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("approval_1");
        when(delegateTask.getId()).thenReturn("TASK_SELF");
        stubProcDefKey("DSN_alloc:1:1", "DSN_alloc");

        when(candidateResolverService.resolveCandidates("DSN_alloc", "approval_1"))
                .thenReturn(List.of("ROLE:BRANCH_HEAD"));
        when(candidateResolverService.resolveCandidateScopeMap("DSN_alloc", "approval_1"))
                .thenReturn(Map.of("ROLE:BRANCH_HEAD", "SELF"));
        when(delegateTask.getVariable("startOrgId")).thenReturn("ORG_A");
        when(userApi.getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "ORG_A")).thenReturn(List.of("E_A1"));

        taskAssignmentListener.notify(delegateTask);

        // 本机构：审批机构 = 发起人机构 ORG_A，候选用户来自该机构
        verify(delegateTask).addCandidateUser("E_A1");
        verify(orgApi, never()).getOrg(anyString());           // SELF 无需查上级
    }

    /**
     * 审批机构归属 PARENT（上级机构）：审批机构取发起人机构的上级，按上级机构过滤候选。
     */
    @Test
    void notify_approveOrgScopeParent_filtersByParentOrg() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc:1:2");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("approval_1");
        when(delegateTask.getId()).thenReturn("TASK_PARENT");
        stubProcDefKey("DSN_alloc:1:2", "DSN_alloc");

        when(candidateResolverService.resolveCandidates("DSN_alloc", "approval_1"))
                .thenReturn(List.of("ROLE:BRANCH_HEAD"));
        when(candidateResolverService.resolveCandidateScopeMap("DSN_alloc", "approval_1"))
                .thenReturn(Map.of("ROLE:BRANCH_HEAD", "PARENT"));
        when(delegateTask.getVariable("startOrgId")).thenReturn("ORG_SUB");
        com.bank.branch.platform.auth.api.dto.OrgDTO org = new com.bank.branch.platform.auth.api.dto.OrgDTO();
        org.setParentOrgCode("ORG_PARENT");
        when(orgApi.getOrg("ORG_SUB")).thenReturn(org);
        when(userApi.getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "ORG_PARENT")).thenReturn(List.of("E_P1"));

        taskAssignmentListener.notify(delegateTask);

        // 上级机构：审批机构 = ORG_SUB 的上级 ORG_PARENT
        verify(delegateTask).addCandidateUser("E_P1");
    }

    /**
     * 审批机构归属 AUTO（按机构层级）：3 级支行 → 上级分行，按上级机构过滤候选。
     */
    @Test
    void notify_approveOrgScopeAuto_routesByLevel() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc:1:5");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("branch_approve");
        when(delegateTask.getId()).thenReturn("TASK_AUTO");
        stubProcDefKey("DSN_alloc:1:5", "DSN_alloc");

        when(candidateResolverService.resolveCandidates("DSN_alloc", "branch_approve"))
                .thenReturn(List.of("ROLE:BRANCH_HEAD"));
        // 历史静态 branch_approve 节点：无显式机构归属(scopeMap 空) → 走机构等级自动解析(AUTO)
        when(delegateTask.getVariable("startOrgId")).thenReturn("ORG_SUB");
        com.bank.branch.platform.auth.api.dto.OrgDTO org = new com.bank.branch.platform.auth.api.dto.OrgDTO();
        org.setOrgLevel(3);
        org.setParentOrgCode("ORG_PARENT");
        when(orgApi.getOrg("ORG_SUB")).thenReturn(org);
        when(userApi.getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "ORG_PARENT")).thenReturn(List.of("E_AUTO"));

        taskAssignmentListener.notify(delegateTask);

        // AUTO：3 级支行审批机构取上级分行 ORG_PARENT
        verify(delegateTask).addCandidateUser("E_AUTO");
    }

    /**
     * 审批机构归属 L2（二级机构）——四级网点发起：沿 P_ID 上溯到 2 级分行，按分行过滤候选。
     * 「实际业务有 4 级机构」后新增的层级：发起上级机构(PARENT)只跳一级到支行(3 级)，
     * L2 则固定上溯到发起机构所属的 2 级分行，让机构负责人审批落到分行。
     */
    @Test
    void notify_approveOrgScopeL2_fromLevel4_walksUpToSecondLevelOrg() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc:1:41");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("branch_approve_l3");
        when(delegateTask.getId()).thenReturn("TASK_L2_L4");
        stubProcDefKey("DSN_alloc:1:41", "DSN_alloc");

        when(candidateResolverService.resolveCandidates("DSN_alloc", "branch_approve_l3"))
                .thenReturn(List.of("ROLE:BRANCH_HEAD"));
        when(candidateResolverService.resolveCandidateScopeMap("DSN_alloc", "branch_approve_l3"))
                .thenReturn(Map.of("ROLE:BRANCH_HEAD", "L2"));
        when(delegateTask.getVariable("startOrgId")).thenReturn("ORG_L4");

        com.bank.branch.platform.auth.api.dto.OrgDTO l4 = new com.bank.branch.platform.auth.api.dto.OrgDTO();
        l4.setOrgLevel(4);
        l4.setParentOrgCode("ORG_L3");
        com.bank.branch.platform.auth.api.dto.OrgDTO l3 = new com.bank.branch.platform.auth.api.dto.OrgDTO();
        l3.setOrgLevel(3);
        l3.setParentOrgCode("ORG_L2");
        com.bank.branch.platform.auth.api.dto.OrgDTO l2 = new com.bank.branch.platform.auth.api.dto.OrgDTO();
        l2.setOrgLevel(2);
        l2.setParentOrgCode("ORG_L1");
        when(orgApi.getOrg("ORG_L4")).thenReturn(l4);
        when(orgApi.getOrg("ORG_L3")).thenReturn(l3);
        when(orgApi.getOrg("ORG_L2")).thenReturn(l2);
        when(userApi.getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "ORG_L2")).thenReturn(List.of("E_L2"));

        taskAssignmentListener.notify(delegateTask);

        // 二级机构：审批机构 = 上溯到的 2 级分行 ORG_L2
        verify(delegateTask).addCandidateUser("E_L2");
    }

    /**
     * L2 从三级支行发起：只上溯一级即到 2 级分行——结果与旧 PARENT 一致（Part B 等价性保证）。
     */
    @Test
    void notify_approveOrgScopeL2_fromLevel3_equalsParentBranch() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc:1:31");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("branch_approve_l3");
        when(delegateTask.getId()).thenReturn("TASK_L2_L3");
        stubProcDefKey("DSN_alloc:1:31", "DSN_alloc");

        when(candidateResolverService.resolveCandidates("DSN_alloc", "branch_approve_l3"))
                .thenReturn(List.of("ROLE:BRANCH_HEAD"));
        when(candidateResolverService.resolveCandidateScopeMap("DSN_alloc", "branch_approve_l3"))
                .thenReturn(Map.of("ROLE:BRANCH_HEAD", "L2"));
        when(delegateTask.getVariable("startOrgId")).thenReturn("ORG_SUB");

        com.bank.branch.platform.auth.api.dto.OrgDTO sub = new com.bank.branch.platform.auth.api.dto.OrgDTO();
        sub.setOrgLevel(3);
        sub.setParentOrgCode("ORG_BR");
        com.bank.branch.platform.auth.api.dto.OrgDTO br = new com.bank.branch.platform.auth.api.dto.OrgDTO();
        br.setOrgLevel(2);
        when(orgApi.getOrg("ORG_SUB")).thenReturn(sub);
        when(orgApi.getOrg("ORG_BR")).thenReturn(br);
        when(userApi.getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "ORG_BR")).thenReturn(List.of("E_BR"));

        taskAssignmentListener.notify(delegateTask);

        verify(delegateTask).addCandidateUser("E_BR");
    }

    /**
     * L2 从二级分行自身发起：命中即 2 级，不再上溯——二级机构 = 本机构。
     */
    @Test
    void notify_approveOrgScopeL2_fromLevel2_isSelf() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc:1:21");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("branch_approve_l3");
        when(delegateTask.getId()).thenReturn("TASK_L2_L2");
        stubProcDefKey("DSN_alloc:1:21", "DSN_alloc");

        when(candidateResolverService.resolveCandidates("DSN_alloc", "branch_approve_l3"))
                .thenReturn(List.of("ROLE:BRANCH_HEAD"));
        when(candidateResolverService.resolveCandidateScopeMap("DSN_alloc", "branch_approve_l3"))
                .thenReturn(Map.of("ROLE:BRANCH_HEAD", "L2"));
        when(delegateTask.getVariable("startOrgId")).thenReturn("ORG_BR");

        com.bank.branch.platform.auth.api.dto.OrgDTO br = new com.bank.branch.platform.auth.api.dto.OrgDTO();
        br.setOrgLevel(2);
        when(orgApi.getOrg("ORG_BR")).thenReturn(br);
        when(userApi.getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "ORG_BR")).thenReturn(List.of("E_SELF"));

        taskAssignmentListener.notify(delegateTask);

        verify(delegateTask).addCandidateUser("E_SELF");
    }

    /**
     * L2 无 2 级上级（总行发起 / 断链）：兜底为发起人本机构，绝不放空导致无人可批。
     */
    @Test
    void notify_approveOrgScopeL2_noSecondLevelAncestor_fallsBackToStartOrg() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc:1:11");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("branch_approve_l3");
        when(delegateTask.getId()).thenReturn("TASK_L2_HQ");
        stubProcDefKey("DSN_alloc:1:11", "DSN_alloc");

        when(candidateResolverService.resolveCandidates("DSN_alloc", "branch_approve_l3"))
                .thenReturn(List.of("ROLE:BRANCH_HEAD"));
        when(candidateResolverService.resolveCandidateScopeMap("DSN_alloc", "branch_approve_l3"))
                .thenReturn(Map.of("ROLE:BRANCH_HEAD", "L2"));
        when(delegateTask.getVariable("startOrgId")).thenReturn("ORG_HQ");

        com.bank.branch.platform.auth.api.dto.OrgDTO hq = new com.bank.branch.platform.auth.api.dto.OrgDTO();
        hq.setOrgLevel(1);
        when(orgApi.getOrg("ORG_HQ")).thenReturn(hq);
        when(userApi.getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "ORG_HQ")).thenReturn(List.of("E_HQ"));

        taskAssignmentListener.notify(delegateTask);

        // 兜底本机构 ORG_HQ
        verify(delegateTask).addCandidateUser("E_HQ");
    }

    /**
     * 候选人模式（任务无 assignee）也要把解析出的「审批机构」记入参与机构快照。
     * <p>背景：原先只在 {@code delegateTask.getAssignee() != null} 时写 WF_PROCESS_ORG，
     * 而层级角色节点走的是候选人模式（ASSIGNEE_=NULL），导致"轮到某分行审批"这一事实
     * 在有人签收前从不进快照 —— 审批流监控页按机构范围过滤时，该分行的秘书看不到这条流程。
     */
    @Test
    void notify_candidateOrgScope_recordsApproveOrgSnapshot() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc:1:71");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("branch_approve_l3");
        when(delegateTask.getId()).thenReturn("TASK_SNAP_PARENT");
        when(delegateTask.getProcessInstanceId()).thenReturn("PROC_SNAP_1");
        stubProcDefKey("DSN_alloc:1:71", "DSN_alloc");

        when(candidateResolverService.resolveCandidates("DSN_alloc", "branch_approve_l3"))
                .thenReturn(List.of("ROLE:BRANCH_HEAD"));
        when(candidateResolverService.resolveCandidateScopeMap("DSN_alloc", "branch_approve_l3"))
                .thenReturn(Map.of("ROLE:BRANCH_HEAD", "PARENT"));
        when(delegateTask.getVariable("startOrgId")).thenReturn("ORG_SUB");
        com.bank.branch.platform.auth.api.dto.OrgDTO org = new com.bank.branch.platform.auth.api.dto.OrgDTO();
        org.setParentOrgCode("ORG_PARENT");
        when(orgApi.getOrg("ORG_SUB")).thenReturn(org);
        when(userApi.getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "ORG_PARENT")).thenReturn(List.of("E_P1"));

        taskAssignmentListener.notify(delegateTask);

        // 审批机构 ORG_PARENT 必须进快照，来源标记 CANDIDATE（区别于已签收的 ASSIGN/CLAIM）
        verify(wfProcessOrgService).recordOrg("PROC_SNAP_1", "ORG_PARENT", "CANDIDATE");
    }

    /**
     * L2（二级机构）候选人模式：快照记的是上溯到的 2 级分行，而非发起支行。
     * 对应线上现象——金台支行(3级)发起，宝鸡分行(2级)负责人审批，分行秘书应能在监控页看到。
     */
    @Test
    void notify_candidateScopeL2_recordsSecondLevelOrgSnapshot() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc:1:72");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("branch_approve_l3");
        when(delegateTask.getId()).thenReturn("TASK_SNAP_L2");
        when(delegateTask.getProcessInstanceId()).thenReturn("PROC_SNAP_2");
        stubProcDefKey("DSN_alloc:1:72", "DSN_alloc");

        when(candidateResolverService.resolveCandidates("DSN_alloc", "branch_approve_l3"))
                .thenReturn(List.of("ROLE:BRANCH_HEAD"));
        when(candidateResolverService.resolveCandidateScopeMap("DSN_alloc", "branch_approve_l3"))
                .thenReturn(Map.of("ROLE:BRANCH_HEAD", "L2"));
        when(delegateTask.getVariable("startOrgId")).thenReturn("ORG_SUB");

        com.bank.branch.platform.auth.api.dto.OrgDTO sub = new com.bank.branch.platform.auth.api.dto.OrgDTO();
        sub.setOrgLevel(3);
        sub.setParentOrgCode("ORG_BR");
        com.bank.branch.platform.auth.api.dto.OrgDTO br = new com.bank.branch.platform.auth.api.dto.OrgDTO();
        br.setOrgLevel(2);
        when(orgApi.getOrg("ORG_SUB")).thenReturn(sub);
        when(orgApi.getOrg("ORG_BR")).thenReturn(br);
        when(userApi.getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "ORG_BR")).thenReturn(List.of("E_BR"));

        taskAssignmentListener.notify(delegateTask);

        verify(wfProcessOrgService).recordOrg("PROC_SNAP_2", "ORG_BR", "CANDIDATE");
    }

    /**
     * 不限机构的候选组节点：没有确定的审批机构，不写快照（否则会把无关机构塞进监控范围）。
     */
    @Test
    void notify_noOrgScope_doesNotRecordApproveOrgSnapshot() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc:1:73");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("approval_2");
        when(delegateTask.getId()).thenReturn("TASK_SNAP_NONE");
        stubProcDefKey("DSN_alloc:1:73", "DSN_alloc");

        when(candidateResolverService.resolveCandidates("DSN_alloc", "approval_2"))
                .thenReturn(List.of("ROLE:CORP_PERF_REV"));

        taskAssignmentListener.notify(delegateTask);

        verify(wfProcessOrgService, never()).recordOrg(anyString(), anyString(), anyString());
    }

    /**
     * 审批机构归属未配置（null）的普通节点：不做机构过滤，照常设置候选组。
     */
    @Test
    void notify_noApproveOrgScope_noOrgFilter() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc:1:3");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("approval_2");
        when(delegateTask.getId()).thenReturn("TASK_NOSCOPE");
        stubProcDefKey("DSN_alloc:1:3", "DSN_alloc");

        when(candidateResolverService.resolveCandidates("DSN_alloc", "approval_2"))
                .thenReturn(List.of("ROLE:CUST_MANAGER"));
        // 无机构归属配置(scopeMap 空) → 不做机构过滤

        taskAssignmentListener.notify(delegateTask);

        // 不限：照常设候选组，不做机构过滤
        verify(delegateTask).addCandidateGroup("ROLE:CUST_MANAGER");
        verify(delegateTask, never()).addCandidateUser(anyString());
    }

    /**
     * 同一节点混合机构归属（用户上报场景）：branch_approve_l3 既有 PARENT(上级机构) 的机构负责人，
     * 又有「不限机构」的公司业绩预审角色(CORP_PERF_REV)。修复前节点级单一 scope 把 PARENT 套到全部
     * 候选，导致 CORP_PERF_REV 也被按上级机构过滤、该角色用户看不到待办。
     * 修复后：PARENT 角色按上级机构过滤为候选用户；不限机构的角色整组作为候选组。
     */
    @Test
    void notify_mixedOrgScope_unscopedRoleStaysWholeCandidateGroup() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc_corp_designer:3:x");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("branch_approve_l3");
        when(delegateTask.getId()).thenReturn("TASK_MIX");
        stubProcDefKey("DSN_alloc_corp_designer:3:x", "DSN_alloc_corp_designer");

        when(candidateResolverService.resolveCandidates("DSN_alloc_corp_designer", "branch_approve_l3"))
                .thenReturn(List.of("ROLE:BRANCH_HEAD", "ROLE:CORP_PERF_REV"));
        // 只有 BRANCH_HEAD 配了 PARENT；CORP_PERF_REV 不在 map = 不限机构
        when(candidateResolverService.resolveCandidateScopeMap("DSN_alloc_corp_designer", "branch_approve_l3"))
                .thenReturn(Map.of("ROLE:BRANCH_HEAD", "PARENT"));
        when(delegateTask.getVariable("startOrgId")).thenReturn("ORG_SUB");
        com.bank.branch.platform.auth.api.dto.OrgDTO org = new com.bank.branch.platform.auth.api.dto.OrgDTO();
        org.setParentOrgCode("ORG_PARENT");
        when(orgApi.getOrg("ORG_SUB")).thenReturn(org);
        when(userApi.getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "ORG_PARENT")).thenReturn(List.of("E_HEAD"));

        taskAssignmentListener.notify(delegateTask);

        // PARENT：按上级机构过滤为候选用户
        verify(delegateTask).addCandidateUser("E_HEAD");
        // 不限机构：整组作候选组（关键修复点——修复前会被 PARENT 误过滤而丢失）
        verify(delegateTask).addCandidateGroup("ROLE:CORP_PERF_REV");
        // PARENT 角色被机构过滤，不再作为整组候选组
        verify(delegateTask, never()).addCandidateGroup("ROLE:BRANCH_HEAD");
    }

    /**
     * 传入变量审批人（VAR，或签）：候选为 VAR:变量名 → 从流程变量取员工，设为候选用户。
     */
    @Test
    void notify_varCandidate_addsCandidateUsersFromVariable() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc:1:7");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("owner_node");
        when(delegateTask.getId()).thenReturn("TASK_VAR");
        stubProcDefKey("DSN_alloc:1:7", "DSN_alloc");

        when(candidateResolverService.resolveCandidates("DSN_alloc", "owner_node"))
                .thenReturn(List.of("VAR:originalOwnerEmpIds"));
        when(delegateTask.getVariable("startOrgId")).thenReturn(null);
        when(delegateTask.getVariable("originalOwnerEmpIds")).thenReturn(List.of("E1", "E2"));

        taskAssignmentListener.notify(delegateTask);

        verify(delegateTask).addCandidateUser("E1");
        verify(delegateTask).addCandidateUser("E2");
        verify(delegateTask, never()).addCandidateGroup(anyString());
    }

    /**
     * GROUP_ALL 多实例当前实例只绑定一个机构组：组内任一负责人可办理，任务名称和机构快照保留组上下文。
     */
    @Test
    void notify_groupInstance_addsGroupUsers_namesTaskAndRecordsOrgSnapshot() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc:1:8");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("original_owner_approve");
        when(delegateTask.getId()).thenReturn("TASK_GROUP_A");
        when(delegateTask.getProcessInstanceId()).thenReturn("PROC_GROUP");
        when(delegateTask.getName()).thenReturn("原业绩所属机构负责人审批");
        stubProcDefKey("DSN_alloc:1:8", "DSN_alloc");
        when(delegateTask.hasVariable(MultiInstanceApproverGroupResolver.VAR_APPROVER_GROUP)).thenReturn(true);
        when(delegateTask.getVariable(MultiInstanceApproverGroupResolver.VAR_APPROVER_GROUP))
                .thenReturn(new ApproverGroupDTO("ORG_A", "机构A", List.of("A1", "A2", "A1")));

        taskAssignmentListener.notify(delegateTask);

        verify(delegateTask).addCandidateUser("A1");
        verify(delegateTask).addCandidateUser("A2");
        verify(delegateTask).setName("原业绩所属机构负责人审批（机构A）");
        verify(wfProcessOrgService).recordOrg("PROC_GROUP", "ORG_A", "CANDIDATE");
        verify(candidateResolverService, never()).resolveCandidates(anyString(), anyString());
        verify(delegateTask, never()).addCandidateGroup(anyString());
    }

    @Test
    void notify_groupInstance_withInvalidGroup_failsClosed() {
        DelegateTask delegateTask = mock(DelegateTask.class);
        when(delegateTask.getProcessDefinitionId()).thenReturn("DSN_alloc:1:9");
        when(delegateTask.getTaskDefinitionKey()).thenReturn("original_owner_approve");
        when(delegateTask.getId()).thenReturn("TASK_GROUP_INVALID");
        when(delegateTask.hasVariable(MultiInstanceApproverGroupResolver.VAR_APPROVER_GROUP)).thenReturn(true);
        when(delegateTask.getVariable(MultiInstanceApproverGroupResolver.VAR_APPROVER_GROUP))
                .thenReturn(new ApproverGroupDTO("", "机构A", List.of("A1")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> taskAssignmentListener.notify(delegateTask))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("机构审批分组");
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
