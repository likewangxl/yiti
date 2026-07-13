package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.TransferInitiateReqDTO;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.entity.WfTaskTransfer;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import com.bank.branch.platform.workflow.mapper.WfTaskTransferMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TaskTransferService#initiate 单元测试。
 * <p>
 * 覆盖两阶段转交发起的核心校验链：任务存在性、单活约束（不可重复发起待认领）、
 * 接收人机构校验、接收人节点办理资格校验（双路：节点候选原始配置 ∪ 任务实际身份链接）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TaskTransferServiceTest {

    @Mock private TaskService taskService;
    @Mock private RepositoryService repositoryService;
    @Mock private WfTaskTransferMapper wfTaskTransferMapper;
    @Mock private BizProcessMapMapper bizProcessMapMapper;
    @Mock private CandidateResolverService candidateResolverService;
    @Mock private OrgApi orgApi;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private UserApi userApi;

    @InjectMocks
    private TaskTransferService taskTransferService;

    private static final String TASK_ID = "TASK_1";
    private static final String PD_ID = "PD_ID_1";
    private static final String PD_KEY = "loan_approve_v1";
    private static final String NODE_KEY = "branch_approve";
    private static final String PID = "PID_1";
    private static final String E_FROM = "E_ASSIGNEE";
    private static final String E_SEC = "E_SEC";
    private static final String E_TO = "E_TO";
    private static final String ORG_A = "ORG_A";

    /**
     * 构建标准任务 mock。字段按 lenient 打桩：不同测试用例在不同校验步骤提前 return/throw，
     * 并非每个用例都会走到用完全部字段的成功路径，避免 Mockito 严格模式误报 UnnecessaryStubbing。
     */
    private Task mockTask() {
        Task task = mock(Task.class);
        lenient().when(task.getProcessDefinitionId()).thenReturn(PD_ID);
        lenient().when(task.getProcessInstanceId()).thenReturn(PID);
        lenient().when(task.getTaskDefinitionKey()).thenReturn(NODE_KEY);
        lenient().when(task.getAssignee()).thenReturn(E_FROM);
        lenient().when(task.getName()).thenReturn("机构负责人审批");
        return task;
    }

    private void mockTaskQuery(Task result) {
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId(anyString())).thenReturn(tq);
        when(tq.singleResult()).thenReturn(result);
    }

    private void mockPdKey() {
        ProcessDefinition pd = mock(ProcessDefinition.class);
        when(pd.getKey()).thenReturn(PD_KEY);
        when(repositoryService.getProcessDefinition(PD_ID)).thenReturn(pd);
    }

    private OrgDTO org(String orgCode) {
        OrgDTO dto = new OrgDTO();
        dto.setOrgCode(orgCode);
        return dto;
    }

    private TransferInitiateReqDTO req(String toEmpId, String reason) {
        TransferInitiateReqDTO r = new TransferInitiateReqDTO();
        r.setToEmpId(toEmpId);
        r.setReason(reason);
        return r;
    }

    /** 正常发起：接收人显式在节点 USER: 候选名单 → 写入 PENDING_ACCEPT，fromEmpId 取 assignee 而非发起人。 */
    @Test
    void initiate_createsPendingAndValidatesReceiver() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_SEC);
        when(currentUserApi.getCurrentOrgCode()).thenReturn(ORG_A);
        mockTaskQuery(mockTask());
        when(wfTaskTransferMapper.selectActiveByTaskId(TASK_ID)).thenReturn(null);
        when(orgApi.getUserMainOrg(E_TO)).thenReturn(org(ORG_A));
        mockPdKey();
        when(candidateResolverService.resolveCandidates(PD_KEY, NODE_KEY)).thenReturn(List.of("USER:" + E_TO));
        when(userApi.getCandidateGroupKeys(E_TO)).thenReturn(Set.of("USER:" + E_TO, "ORG:" + ORG_A));
        BizProcessMap map = new BizProcessMap();
        map.setBusinessKey("LOAN:LA001");
        map.setBizType("LOAN");
        when(bizProcessMapMapper.selectByProcessInstanceId(PID)).thenReturn(map);

        String transferId = taskTransferService.initiate(TASK_ID, req(E_TO, "出差，请代为处理"));

        assertThat(transferId).isNotBlank();
        ArgumentCaptor<WfTaskTransfer> captor = ArgumentCaptor.forClass(WfTaskTransfer.class);
        verify(wfTaskTransferMapper).insert(captor.capture());
        WfTaskTransfer inserted = captor.getValue();
        assertThat(inserted.getStatus()).isEqualTo("PENDING_ACCEPT");
        assertThat(inserted.getToEmpId()).isEqualTo(E_TO);
        assertThat(inserted.getFromEmpId()).isEqualTo(E_FROM);
        assertThat(inserted.getInitiatorEmpId()).isEqualTo(E_SEC);
        assertThat(inserted.getTaskId()).isEqualTo(TASK_ID);
        assertThat(inserted.getProcessInstanceId()).isEqualTo(PID);
        assertThat(inserted.getNodeKey()).isEqualTo(NODE_KEY);
        assertThat(inserted.getOrgCode()).isEqualTo(ORG_A);
        assertThat(inserted.getTransferReason()).isEqualTo("出差，请代为处理");
        assertThat(inserted.getBusinessKey()).isEqualTo("LOAN:LA001");
        assertThat(inserted.getBizType()).isEqualTo("LOAN");
        assertThat(inserted.getInitiatedTime()).isNotNull();

        verify(taskService).addComment(TASK_ID, PID, "TRANSFER_INITIATED", "出差，请代为处理");
    }

    /** 任务不存在 → WF-40403。 */
    @Test
    void initiate_taskNotFound_throws() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_SEC);
        when(currentUserApi.getCurrentOrgCode()).thenReturn(ORG_A);
        mockTaskQuery(null);

        assertThatThrownBy(() -> taskTransferService.initiate(TASK_ID, req(E_TO, "忙")))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40403");
    }

    /** 接收人主机构 ≠ 发起人机构 → 拒绝（WF-40911）。 */
    @Test
    void initiate_rejectsReceiverOutsideOrg() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_SEC);
        when(currentUserApi.getCurrentOrgCode()).thenReturn(ORG_A);
        mockTaskQuery(mockTask());
        when(wfTaskTransferMapper.selectActiveByTaskId(TASK_ID)).thenReturn(null);
        when(orgApi.getUserMainOrg(E_TO)).thenReturn(org("ORG_B"));

        assertThatThrownBy(() -> taskTransferService.initiate(TASK_ID, req(E_TO, "忙")))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40911");
    }

    /** 接收人机构信息查不到（null）同样按不在本机构拒绝。 */
    @Test
    void initiate_rejectsReceiverWithNoMainOrg() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_SEC);
        when(currentUserApi.getCurrentOrgCode()).thenReturn(ORG_A);
        mockTaskQuery(mockTask());
        when(wfTaskTransferMapper.selectActiveByTaskId(TASK_ID)).thenReturn(null);
        when(orgApi.getUserMainOrg(E_TO)).thenReturn(null);

        assertThatThrownBy(() -> taskTransferService.initiate(TASK_ID, req(E_TO, "忙")))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40911");
    }

    /** 已存在 PENDING_ACCEPT 记录 → 拒绝（WF-40910），单活约束，不再校验后续步骤。 */
    @Test
    void initiate_rejectsWhenAlreadyPending() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_SEC);
        when(currentUserApi.getCurrentOrgCode()).thenReturn(ORG_A);
        mockTaskQuery(mockTask());
        WfTaskTransfer existing = new WfTaskTransfer();
        existing.setId("EXIST_1");
        existing.setStatus("PENDING_ACCEPT");
        when(wfTaskTransferMapper.selectActiveByTaskId(TASK_ID)).thenReturn(existing);

        assertThatThrownBy(() -> taskTransferService.initiate(TASK_ID, req(E_TO, "忙")))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40910");
    }

    /**
     * 接收人不在该节点候选组配置里、也不在任务实际身份链接里 → 拒绝（WF-40912）。
     * <p>
     * 节点候选（ROLE:OTHER_ROLE）与接收人候选组标识（不含 ROLE:OTHER_ROLE）无交集，
     * 且任务身份链接为空 → 判定接收人无该节点办理资格。
     * </p>
     */
    @Test
    void initiate_rejectsReceiverNotEligibleForNode() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_SEC);
        when(currentUserApi.getCurrentOrgCode()).thenReturn(ORG_A);
        mockTaskQuery(mockTask());
        when(wfTaskTransferMapper.selectActiveByTaskId(TASK_ID)).thenReturn(null);
        when(orgApi.getUserMainOrg(E_TO)).thenReturn(org(ORG_A));
        mockPdKey();
        when(candidateResolverService.resolveCandidates(PD_KEY, NODE_KEY)).thenReturn(List.of("ROLE:OTHER_ROLE"));
        when(userApi.getCandidateGroupKeys(E_TO))
                .thenReturn(Set.of("ROLE:UNRELATED_ROLE", "USER:" + E_TO, "ORG:" + ORG_A));
        when(taskService.getIdentityLinksForTask(TASK_ID)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> taskTransferService.initiate(TASK_ID, req(E_TO, "忙")))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40912");
    }

    /**
     * 接收人节点资格达标路径一：节点候选（ROLE:BRANCH_HEAD）与接收人候选组标识
     * （{@code UserApi.getCandidateGroupKeys} 计算出的 ROLE:BRANCH_HEAD）存在交集 → 放行。
     */
    @Test
    void initiate_acceptsReceiverViaRoleCandidateIntersection() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_SEC);
        when(currentUserApi.getCurrentOrgCode()).thenReturn(ORG_A);
        mockTaskQuery(mockTask());
        when(wfTaskTransferMapper.selectActiveByTaskId(TASK_ID)).thenReturn(null);
        when(orgApi.getUserMainOrg(E_TO)).thenReturn(org(ORG_A));
        mockPdKey();
        when(candidateResolverService.resolveCandidates(PD_KEY, NODE_KEY)).thenReturn(List.of("ROLE:BRANCH_HEAD"));
        when(userApi.getCandidateGroupKeys(E_TO))
                .thenReturn(Set.of("ROLE:BRANCH_HEAD", "USER:" + E_TO, "ORG:" + ORG_A));

        String transferId = taskTransferService.initiate(TASK_ID, req(E_TO, "忙"));

        assertThat(transferId).isNotBlank();
        verify(wfTaskTransferMapper).insert(org.mockito.ArgumentMatchers.any(WfTaskTransfer.class));
    }

    /**
     * 接收人节点资格达标路径二：节点原始配置候选（ROLE:OTHER_ROLE）不含接收人，但任务实际身份链接
     * （{@code taskService.getIdentityLinksForTask}）里有一条 candidate 类型、userId 直接等于接收人
     * 的链接 —— 反映 TaskAssignmentListener 按机构过滤后把候选具体化为该用户 → 仍应放行。
     */
    @Test
    void initiate_acceptsReceiverViaTaskIdentityLinkDirectUser() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_SEC);
        when(currentUserApi.getCurrentOrgCode()).thenReturn(ORG_A);
        mockTaskQuery(mockTask());
        when(wfTaskTransferMapper.selectActiveByTaskId(TASK_ID)).thenReturn(null);
        when(orgApi.getUserMainOrg(E_TO)).thenReturn(org(ORG_A));
        mockPdKey();
        when(candidateResolverService.resolveCandidates(PD_KEY, NODE_KEY)).thenReturn(List.of("ROLE:OTHER_ROLE"));
        when(userApi.getCandidateGroupKeys(E_TO)).thenReturn(Set.of("USER:" + E_TO));

        IdentityLink link = mock(IdentityLink.class);
        when(link.getType()).thenReturn("candidate");
        when(link.getUserId()).thenReturn(E_TO);
        when(taskService.getIdentityLinksForTask(TASK_ID)).thenReturn(List.of(link));

        String transferId = taskTransferService.initiate(TASK_ID, req(E_TO, "忙"));

        assertThat(transferId).isNotBlank();
        verify(wfTaskTransferMapper).insert(org.mockito.ArgumentMatchers.any(WfTaskTransfer.class));
    }

    /** hasPendingTransfer 直接透传 mapper.selectActiveByTaskId 是否非空。 */
    @Test
    void hasPendingTransfer_delegatesToMapper() {
        when(wfTaskTransferMapper.selectActiveByTaskId(TASK_ID)).thenReturn(new WfTaskTransfer());
        assertThat(taskTransferService.hasPendingTransfer(TASK_ID)).isTrue();

        when(wfTaskTransferMapper.selectActiveByTaskId("TASK_2")).thenReturn(null);
        assertThat(taskTransferService.hasPendingTransfer("TASK_2")).isFalse();
    }
}
