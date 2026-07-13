package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
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
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock private WfProcessOrgService wfProcessOrgService;
    @Mock private NotifyApi notifyApi;

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
    private static final String TRANSFER_ID = "TRANSFER_1";

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

    /**
     * 接收人查不到主机构 → 同样按不在本机构拒绝（WF-40911）。
     * <p>
     * 真实 {@code OrgApi.getUserMainOrg} 在接收人无 {@code EXT_USER_ORG} 主机构记录时不会返回
     * {@code null}，而是抛 {@code BizException(AUTH-40403, "用户不存在")}——桩必须还原这个真实契约
     * （抛异常而非返回 null），否则测的是一个不可能出现的假场景。
     * </p>
     */
    @Test
    void initiate_rejectsReceiverWithNoMainOrg() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_SEC);
        when(currentUserApi.getCurrentOrgCode()).thenReturn(ORG_A);
        mockTaskQuery(mockTask());
        when(wfTaskTransferMapper.selectActiveByTaskId(TASK_ID)).thenReturn(null);
        when(orgApi.getUserMainOrg(E_TO)).thenThrow(new BizException("AUTH-40403", "用户不存在"));

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
     * 单活约束的 DB 侧兜底：预检查（{@code selectActiveByTaskId}）读到 null 通过后，
     * 并发的另一发起请求先一步插入并占用了 {@code uk_active_task} 唯一索引，本次
     * {@code insert} 命中唯一索引冲突抛 {@code DataIntegrityViolationException}（
     * {@code DuplicateKeyException} 是其子类）—— 须兜成与预检查一致的 WF-40910，
     * 而不是把底层 DB 异常泄漏给调用方。
     */
    @Test
    void initiate_rejectsWhenInsertRaceLosesToUniqueIndex() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_SEC);
        when(currentUserApi.getCurrentOrgCode()).thenReturn(ORG_A);
        mockTaskQuery(mockTask());
        when(wfTaskTransferMapper.selectActiveByTaskId(TASK_ID)).thenReturn(null);
        when(orgApi.getUserMainOrg(E_TO)).thenReturn(org(ORG_A));
        mockPdKey();
        when(candidateResolverService.resolveCandidates(PD_KEY, NODE_KEY)).thenReturn(List.of("USER:" + E_TO));
        when(userApi.getCandidateGroupKeys(E_TO)).thenReturn(Set.of("USER:" + E_TO));
        when(wfTaskTransferMapper.insert(any(WfTaskTransfer.class))).thenThrow(new DuplicateKeyException("dup"));

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
     * 接收人节点资格达标——无候选身份链接兜底路径：任务完全没有 candidate 身份链接（
     * {@code getIdentityLinksForTask} 未打桩，Mockito 默认返回空列表，反映"节点从未被
     * 机构收窄过"，如纯直接指派节点）→ 回退节点原始配置，节点候选（ROLE:BRANCH_HEAD）
     * 与接收人候选组标识存在交集 → 放行。
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
        verify(wfTaskTransferMapper).insert(any(WfTaskTransfer.class));
    }

    /**
     * 接收人节点资格达标——权威身份链接（userId 直接匹配）：任务实际身份链接
     * （{@code taskService.getIdentityLinksForTask}）里有一条 candidate 类型、userId 直接等于接收人
     * 的链接（反映 TaskAssignmentListener 按机构过滤后把候选具体化为该用户），节点原始配置
     * （ROLE:OTHER_ROLE）故意不含接收人，用来证明放行来自权威链接本身、不是回退节点配置 → 仍应放行。
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
        verify(wfTaskTransferMapper).insert(any(WfTaskTransfer.class));
    }

    /**
     * 接收人节点资格达标——权威身份链接（groupId 命中接收人候选组标识）：任务实际候选身份链接
     * 保留为组（{@code addCandidateGroup}，未被机构收窄具体化为用户），groupId="ROLE:BRANCH_HEAD"
     * 命中接收人候选组标识；节点原始配置（ROLE:OTHER_ROLE）故意不含接收人，证明放行来自权威链接
     * 本身 → 仍应放行。
     */
    @Test
    void initiate_acceptsReceiverViaTaskIdentityLinkGroupMatch() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_SEC);
        when(currentUserApi.getCurrentOrgCode()).thenReturn(ORG_A);
        mockTaskQuery(mockTask());
        when(wfTaskTransferMapper.selectActiveByTaskId(TASK_ID)).thenReturn(null);
        when(orgApi.getUserMainOrg(E_TO)).thenReturn(org(ORG_A));
        mockPdKey();
        when(candidateResolverService.resolveCandidates(PD_KEY, NODE_KEY)).thenReturn(List.of("ROLE:OTHER_ROLE"));
        when(userApi.getCandidateGroupKeys(E_TO))
                .thenReturn(Set.of("ROLE:BRANCH_HEAD", "USER:" + E_TO, "ORG:" + ORG_A));

        IdentityLink link = mock(IdentityLink.class);
        when(link.getType()).thenReturn("candidate");
        when(link.getGroupId()).thenReturn("ROLE:BRANCH_HEAD");
        when(taskService.getIdentityLinksForTask(TASK_ID)).thenReturn(List.of(link));

        String transferId = taskTransferService.initiate(TASK_ID, req(E_TO, "忙"));

        assertThat(transferId).isNotBlank();
        verify(wfTaskTransferMapper).insert(any(WfTaskTransfer.class));
    }

    /**
     * 越权绕过场景关闭校验：节点原始配置（ROLE:BRANCH_HEAD）未经机构过滤，接收人恰好也持有
     * 该角色（在 receiverKeys 里），但任务实际身份链接（{@code getIdentityLinksForTask}）已被
     * {@code TaskAssignmentListener} 按机构收窄为另一具体用户（{@code E_OTHER_ORG_USER}，反映
     * 秘书代发起时接收人机构 == 发起人机构、但节点真实候选被收窄到另一机构的场景）——接收人不在
     * 这份权威名单里 → 必须拒绝（WF-40912），不能因为节点原始配置里存在同角色就用「||」短路放行。
     */
    @Test
    void initiate_rejectsReceiverWhenTaskCandidateNarrowedToOtherUser() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_SEC);
        when(currentUserApi.getCurrentOrgCode()).thenReturn(ORG_A);
        mockTaskQuery(mockTask());
        when(wfTaskTransferMapper.selectActiveByTaskId(TASK_ID)).thenReturn(null);
        when(orgApi.getUserMainOrg(E_TO)).thenReturn(org(ORG_A));
        mockPdKey();
        when(candidateResolverService.resolveCandidates(PD_KEY, NODE_KEY)).thenReturn(List.of("ROLE:BRANCH_HEAD"));
        when(userApi.getCandidateGroupKeys(E_TO))
                .thenReturn(Set.of("ROLE:BRANCH_HEAD", "USER:" + E_TO, "ORG:" + ORG_A));

        IdentityLink link = mock(IdentityLink.class);
        when(link.getType()).thenReturn("candidate");
        when(link.getUserId()).thenReturn("E_OTHER_ORG_USER");
        when(taskService.getIdentityLinksForTask(TASK_ID)).thenReturn(List.of(link));

        assertThatThrownBy(() -> taskTransferService.initiate(TASK_ID, req(E_TO, "忙")))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40912");
    }

    /** hasPendingTransfer 直接透传 mapper.selectActiveByTaskId 是否非空。 */
    @Test
    void hasPendingTransfer_delegatesToMapper() {
        when(wfTaskTransferMapper.selectActiveByTaskId(TASK_ID)).thenReturn(new WfTaskTransfer());
        assertThat(taskTransferService.hasPendingTransfer(TASK_ID)).isTrue();

        when(wfTaskTransferMapper.selectActiveByTaskId("TASK_2")).thenReturn(null);
        assertThat(taskTransferService.hasPendingTransfer("TASK_2")).isFalse();
    }

    // ==================== accept ====================

    /** 构建一条标准 PENDING_ACCEPT 转交记录，接收人为 E_TO。 */
    private WfTaskTransfer pendingTransfer() {
        WfTaskTransfer t = new WfTaskTransfer();
        t.setId(TRANSFER_ID);
        t.setProcessInstanceId(PID);
        t.setTaskId(TASK_ID);
        t.setNodeKey(NODE_KEY);
        t.setNodeName("机构负责人审批");
        t.setFromEmpId(E_FROM);
        t.setInitiatorEmpId(E_SEC);
        t.setToEmpId(E_TO);
        t.setOrgCode(ORG_A);
        t.setStatus("PENDING_ACCEPT");
        t.setBizType("LOAN");
        t.setBusinessKey("LOAN:LA001");
        return t;
    }

    /**
     * 认领成功：乐观更新命中（返回1）→ 依次 setAssignee、biz_process_map.currentAssignee 改为接收人、
     * 记参与机构（source=TRANSFER）、加认领评论、通知发起人。
     */
    @Test
    void accept_success_setsAssigneeUpdatesMapRecordsOrgAndNotifies() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_TO);
        when(wfTaskTransferMapper.selectById(TRANSFER_ID)).thenReturn(pendingTransfer());
        when(wfTaskTransferMapper.updateStatusIfPending(eq(TRANSFER_ID), eq("ACCEPTED"), isNull(), any(LocalDateTime.class)))
                .thenReturn(1);
        BizProcessMap map = new BizProcessMap();
        map.setId("MAP_1");
        map.setProcessInstanceId(PID);
        map.setCurrentAssignee(E_FROM);
        when(bizProcessMapMapper.selectByProcessInstanceId(PID)).thenReturn(map);

        taskTransferService.accept(TRANSFER_ID);

        verify(taskService).setAssignee(TASK_ID, E_TO);

        ArgumentCaptor<BizProcessMap> mapCaptor = ArgumentCaptor.forClass(BizProcessMap.class);
        verify(bizProcessMapMapper).updateById(mapCaptor.capture());
        assertThat(mapCaptor.getValue().getCurrentAssignee()).isEqualTo(E_TO);

        verify(wfProcessOrgService).record(PID, E_TO, "TRANSFER");
        verify(taskService).addComment(TASK_ID, PID, "TRANSFER_ACCEPTED", null);

        ArgumentCaptor<NotificationCmd> notifyCaptor = ArgumentCaptor.forClass(NotificationCmd.class);
        verify(notifyApi).sendNotification(notifyCaptor.capture());
        assertThat(notifyCaptor.getValue().getTargetEmpId()).isEqualTo(E_SEC);
    }

    /**
     * biz_process_map 查不到映射（map==null）时跳过更新，不阻断认领主流程。
     */
    @Test
    void accept_success_skipsMapUpdateWhenMapNull() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_TO);
        when(wfTaskTransferMapper.selectById(TRANSFER_ID)).thenReturn(pendingTransfer());
        when(wfTaskTransferMapper.updateStatusIfPending(eq(TRANSFER_ID), eq("ACCEPTED"), isNull(), any(LocalDateTime.class)))
                .thenReturn(1);
        when(bizProcessMapMapper.selectByProcessInstanceId(PID)).thenReturn(null);

        taskTransferService.accept(TRANSFER_ID);

        verify(taskService).setAssignee(TASK_ID, E_TO);
        verify(bizProcessMapMapper, never()).updateById(any(BizProcessMap.class));
    }

    /**
     * 并发冲突：乐观更新未命中（updateStatusIfPending 返回0，说明状态已被其他并发请求改变）
     * → 抛 WF-40915 冲突异常，且绝不能推进到 setAssignee（避免并发败者仍改写 assignee）。
     */
    @Test
    void accept_concurrentLoser_throwsAndNeverSetsAssignee() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_TO);
        when(wfTaskTransferMapper.selectById(TRANSFER_ID)).thenReturn(pendingTransfer());
        when(wfTaskTransferMapper.updateStatusIfPending(eq(TRANSFER_ID), eq("ACCEPTED"), isNull(), any(LocalDateTime.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> taskTransferService.accept(TRANSFER_ID))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40915");

        verify(taskService, never()).setAssignee(anyString(), anyString());
        verifyNoInteractions(wfProcessOrgService, notifyApi);
    }

    /** 非接收人尝试认领 → 拒绝（WF-40303），不触发乐观更新。 */
    @Test
    void accept_rejectsNonReceiver() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E_OTHER");
        when(wfTaskTransferMapper.selectById(TRANSFER_ID)).thenReturn(pendingTransfer());

        assertThatThrownBy(() -> taskTransferService.accept(TRANSFER_ID))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40303");

        verify(wfTaskTransferMapper, never()).updateStatusIfPending(anyString(), anyString(), any(), any());
        verify(taskService, never()).setAssignee(anyString(), anyString());
    }

    /** 转交记录不存在 → WF-40914。 */
    @Test
    void accept_rejectsWhenTransferNotFound() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_TO);
        when(wfTaskTransferMapper.selectById(TRANSFER_ID)).thenReturn(null);

        assertThatThrownBy(() -> taskTransferService.accept(TRANSFER_ID))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40914");

        verify(taskService, never()).setAssignee(anyString(), anyString());
    }

    /** 转交已非 PENDING_ACCEPT（已认领/已拒绝/已撤销）→ WF-40914，不可重复认领。 */
    @Test
    void accept_rejectsWhenAlreadyProcessed() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(E_TO);
        WfTaskTransfer t = pendingTransfer();
        t.setStatus("ACCEPTED");
        when(wfTaskTransferMapper.selectById(TRANSFER_ID)).thenReturn(t);

        assertThatThrownBy(() -> taskTransferService.accept(TRANSFER_ID))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40914");

        verify(taskService, never()).setAssignee(anyString(), anyString());
    }
}
