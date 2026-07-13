package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.TransferInitiateReqDTO;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.entity.WfTaskTransfer;
import com.bank.branch.platform.workflow.enums.WfErrorCode;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import com.bank.branch.platform.workflow.mapper.WfTaskTransferMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 任务转交（两阶段：发起待认领 + 接收人认领/拒绝）— 发起侧服务。
 * <p>
 * 与 {@link TaskOperationService#transferTask} 一步到位直接改 assignee 不同：本服务只落
 * {@code WF_TASK_TRANSFER} 待认领记录（status=PENDING_ACCEPT），不改变 Flowable 任务的
 * assignee，须接收人认领后才真正转移办理权（认领/拒绝逻辑见后续任务）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskTransferService {

    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final WfTaskTransferMapper wfTaskTransferMapper;
    private final BizProcessMapMapper bizProcessMapMapper;
    private final CandidateResolverService candidateResolverService;
    private final OrgApi orgApi;
    private final CurrentUserApi currentUserApi;
    /**
     * 按任意 empId 计算候选组标识（{@code ROLE:}/{@code USER:}/{@code ORG:} 前缀，
     * 口径对齐 {@link CandidateResolverService#resolveCandidates}）。
     * <p>
     * 用于校验「接收人 ∈ 该节点可办理者」——发起转交时接收人未必是当前登录用户，
     * 不能用 {@code CurrentUserApi.getCurrentCandidateGroupKeys()}（只读 ThreadLocal 里
     * 当前登录用户）。{@code UserApi.getCandidateGroupKeys(empId)} 是 auth 模块专为「按任意
     * empId 查库计算工作流候选组」提供的能力，格式与 CandidateResolverService 输出对齐。
     * </p>
     */
    private final UserApi userApi;

    private static final String STATUS_PENDING_ACCEPT = "PENDING_ACCEPT";

    /**
     * 发起两阶段转交。
     * <p>
     * 校验链（任一不通过即抛异常，不写库）：
     * <ol>
     *   <li>任务存在（WF-40403）</li>
     *   <li>该任务无正在生效的待认领转交（WF-40910，单活约束）</li>
     *   <li>接收人 ∈ 本机构：机构-用户 1:1，接收人主机构编码须等于发起人机构编码（WF-40911）</li>
     *   <li>接收人 ∈ 该节点可办理者（WF-40912，见 {@link #isEligibleReceiver}）</li>
     * </ol>
     * 通过后写入 {@code WF_TASK_TRANSFER} 记录（status=PENDING_ACCEPT）并在任务上加转交发起评论留痕。
     * </p>
     * <p>
     * {@code fromEmpId} 取当前任务 {@code assignee}（被转出者），<b>不是</b>发起人 —— 秘书代为
     * 发起转交时，发起人（{@code initiatorEmpId}）可能不同于被转出的实际办理人。
     * </p>
     *
     * @param taskId 任务ID
     * @param req    转交发起请求（接收人工号 + 原因）
     * @return 转交记录ID
     * @throws BizException WF-40403 任务不存在；WF-40910 已有待认领转交；
     *                       WF-40911 接收人不在本机构；WF-40912 接收人无该节点办理资格
     */
    public String initiate(String taskId, TransferInitiateReqDTO req) {
        String initiator = currentUserApi.getCurrentEmpId();
        String initiatorOrg = currentUserApi.getCurrentOrgCode();
        String toEmpId = req.getToEmpId();

        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(WfErrorCode.TASK_NOT_FOUND.getCode(), WfErrorCode.TASK_NOT_FOUND.getMessage());
        }

        // 单活校验：同一任务不能同时存在两条待认领转交
        if (wfTaskTransferMapper.selectActiveByTaskId(taskId) != null) {
            throw new BizException(WfErrorCode.TRANSFER_ALREADY_PENDING.getCode(),
                    WfErrorCode.TRANSFER_ALREADY_PENDING.getMessage());
        }

        // 接收人 ∈ 本机构：机构-用户为 1:1，主机构编码须等于发起人机构编码
        OrgDTO toOrg = orgApi.getUserMainOrg(toEmpId);
        if (toOrg == null || !Objects.equals(initiatorOrg, toOrg.getOrgCode())) {
            throw new BizException(WfErrorCode.TRANSFER_RECEIVER_NOT_IN_ORG.getCode(),
                    WfErrorCode.TRANSFER_RECEIVER_NOT_IN_ORG.getMessage());
        }

        // 接收人 ∈ 该节点可办理者
        String pdKey = resolvePdKey(task.getProcessDefinitionId());
        List<String> nodeCandidates = candidateResolverService.resolveCandidates(pdKey, task.getTaskDefinitionKey());
        if (!isEligibleReceiver(taskId, nodeCandidates, toEmpId)) {
            throw new BizException(WfErrorCode.TRANSFER_RECEIVER_NOT_CANDIDATE.getCode(),
                    WfErrorCode.TRANSFER_RECEIVER_NOT_CANDIDATE.getMessage());
        }

        WfTaskTransfer t = new WfTaskTransfer();
        t.setId(UUID.randomUUID().toString().replace("-", ""));
        t.setProcessInstanceId(task.getProcessInstanceId());
        t.setTaskId(taskId);
        t.setNodeKey(task.getTaskDefinitionKey());
        t.setNodeName(task.getName());
        t.setFromEmpId(task.getAssignee());
        t.setInitiatorEmpId(initiator);
        t.setToEmpId(toEmpId);
        t.setOrgCode(initiatorOrg);
        t.setStatus(STATUS_PENDING_ACCEPT);
        t.setTransferReason(req.getReason());
        t.setInitiatedTime(LocalDateTime.now());

        // 补 businessKey/bizType（收发件箱展示用），查不到映射不阻断发起
        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(task.getProcessInstanceId());
        if (map != null) {
            t.setBusinessKey(map.getBusinessKey());
            t.setBizType(map.getBizType());
        }
        wfTaskTransferMapper.insert(t);

        taskService.addComment(taskId, task.getProcessInstanceId(), "TRANSFER_INITIATED", req.getReason());
        log.info("转交发起: taskId={}, from={}, to={}, initiator={}, transferId={}",
                taskId, t.getFromEmpId(), toEmpId, initiator, t.getId());
        return t.getId();
    }

    /**
     * 是否存在待认领转交。
     *
     * @param taskId 任务ID
     * @return true 表示该任务存在 PENDING_ACCEPT 记录
     */
    public boolean hasPendingTransfer(String taskId) {
        return wfTaskTransferMapper.selectActiveByTaskId(taskId) != null;
    }

    /**
     * Flowable 7 默认以 UUID 作 processDefinitionId（无 ":" 分隔），不能 split(":")[0] 取 KEY，
     * 须走 RepositoryService 反查 {@code ProcessDefinition.getKey()} 拿真实 BPMN KEY，
     * 与 {@code TaskAssignmentListener} 解析候选组时同法，保证 pdKey 口径一致。
     */
    private String resolvePdKey(String processDefinitionId) {
        ProcessDefinition pd = repositoryService.getProcessDefinition(processDefinitionId);
        return pd != null ? pd.getKey() : processDefinitionId;
    }

    /**
     * 接收人资格校验：接收人 ∈ 该节点可办理者。
     * <p>
     * Flowable IDM 关闭、候选模型为前缀化标识（{@code ROLE:xxx}/{@code ORG:xxx}/{@code USER:xxx}，
     * 见 {@link CandidateResolverService#resolveCandidates}）。双路校验取「或」，任一命中即合格：
     * </p>
     * <ol>
     *   <li><b>节点配置候选</b>（{@code nodeCandidates}，未经机构过滤的原始配置）：接收人显式在
     *       {@code USER:} 名单，或接收人按 {@link UserApi#getCandidateGroupKeys} 计算出的候选组
     *       标识（同样 ROLE:/USER:/ORG: 前缀口径）与节点候选存在交集；</li>
     *   <li><b>任务实际身份链接</b>（{@code taskService.getIdentityLinksForTask}）：
     *       {@code TaskAssignmentListener} 在任务创建时可能按发起人机构对候选做二次过滤
     *       （如 branch_approve 把 ROLE 展开为具体 {@code addCandidateUser}），此时该任务实例
     *       真实候选集合可能比节点原始配置更窄或已具体化为用户，只查节点配置会漏判「机构过滤后
     *       其实不具备资格 / 已具体化为其他人」的场景，故额外核对任务上真实挂的 candidate 身份链接
     *       （type=candidate 的 userId 直接匹配，或 groupId 命中接收人候选组标识）。</li>
     * </ol>
     */
    private boolean isEligibleReceiver(String taskId, List<String> nodeCandidates, String toEmpId) {
        Set<String> receiverKeys = userApi.getCandidateGroupKeys(toEmpId);

        if (candidateContains(nodeCandidates, receiverKeys, toEmpId)) {
            return true;
        }

        List<IdentityLink> links = taskService.getIdentityLinksForTask(taskId);
        if (links != null) {
            for (IdentityLink link : links) {
                if (!IdentityLinkType.CANDIDATE.equals(link.getType())) {
                    continue;
                }
                if (toEmpId.equals(link.getUserId())) {
                    return true;
                }
                if (link.getGroupId() != null && receiverKeys.contains(link.getGroupId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 候选列表是否包含接收人：接收人本人的 {@code USER:} 前缀精确命中，或接收人的候选组标识
     * （{@code receiverKeys}）与候选列表存在交集（覆盖 ROLE:/ORG: 前缀）。
     */
    private boolean candidateContains(List<String> candidates, Set<String> receiverKeys, String toEmpId) {
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        if (candidates.contains("USER:" + toEmpId)) {
            return true;
        }
        if (receiverKeys == null || receiverKeys.isEmpty()) {
            return false;
        }
        for (String c : candidates) {
            if (receiverKeys.contains(c)) {
                return true;
            }
        }
        return false;
    }
}
