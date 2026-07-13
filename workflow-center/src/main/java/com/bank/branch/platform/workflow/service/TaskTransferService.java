package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import com.bank.branch.platform.workflow.api.dto.TransferInitiateReqDTO;
import com.bank.branch.platform.workflow.api.dto.TransferItemDTO;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 任务转交（两阶段：发起待认领 + 接收人认领/拒绝）— 发起侧服务。
 * <p>
 * 旧单阶段「一步到位直接改 assignee」的转交（{@code TaskOperationService#transferTask}）已下线；
 * 本服务只落 {@code WF_TASK_TRANSFER} 待认领记录（status=PENDING_ACCEPT），不改变 Flowable 任务的
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
    /** 参与机构快照写入唯一入口，认领成功后记录接收人的主机构参与该流程实例（source=TRANSFER）。 */
    private final WfProcessOrgService wfProcessOrgService;
    /** 认领成功后通知发起人（用于秘书代发起、原办理人等场景知悉转交结果）。 */
    private final NotifyApi notifyApi;

    private static final String STATUS_PENDING_ACCEPT = "PENDING_ACCEPT";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_CANCELLED = "CANCELLED";

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
        // OrgApi.getUserMainOrg 在接收人无 EXT_USER_ORG 主机构记录时不返回 null，而是抛
        // BizException(AUTH-40403 用户不存在)——须兜住转译为本模块的 WF-40911，否则会把
        // auth 模块的原始异常泄漏给调用方（错模块、错语义）。
        OrgDTO toOrg;
        try {
            toOrg = orgApi.getUserMainOrg(toEmpId);
        } catch (BizException e) {
            throw new BizException(WfErrorCode.TRANSFER_RECEIVER_NOT_IN_ORG.getCode(),
                    WfErrorCode.TRANSFER_RECEIVER_NOT_IN_ORG.getMessage());
        }
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
        // 单活约束在 DB 侧还有一道生成列+唯一索引兜底（uk_active_task，见 ddl-workflow-monitor.sql）：
        // 上面的先查后插存在竞态窗口（并发 initiate 都读到 selectActiveByTaskId==null），
        // 竞态败者在这里插入时会命中唯一索引冲突，兜成与预检查一致的 WF-40910，而不是让
        // DataIntegrityViolationException 原始泄漏给调用方。
        try {
            wfTaskTransferMapper.insert(t);
        } catch (DataIntegrityViolationException e) {
            throw new BizException(WfErrorCode.TRANSFER_ALREADY_PENDING.getCode(),
                    WfErrorCode.TRANSFER_ALREADY_PENDING.getMessage());
        }

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
     * 转交收件箱：当前登录用户待认领的转交任务列表（{@code WfTaskTransferMapper#selectInbox}，
     * status=PENDING_ACCEPT，按发起时间倒序）。
     *
     * @return 展示项列表，可能为空
     */
    public List<TransferItemDTO> listInbox() {
        String me = currentUserApi.getCurrentEmpId();
        return wfTaskTransferMapper.selectInbox(me).stream().map(this::toItemDTO).toList();
    }

    /**
     * 转交发件箱：当前登录用户发起的转交任务列表（{@code WfTaskTransferMapper#selectOutbox}，
     * status ∈ {PENDING_ACCEPT, ACCEPTED}，按发起时间倒序）。
     *
     * @return 展示项列表，可能为空
     */
    public List<TransferItemDTO> listOutbox() {
        String me = currentUserApi.getCurrentEmpId();
        return wfTaskTransferMapper.selectOutbox(me).stream().map(this::toItemDTO).toList();
    }

    /** 实体 → 展示 DTO，收件箱/发件箱共用同一转换，不把实体直接暴露给 Controller/前端。 */
    private TransferItemDTO toItemDTO(WfTaskTransfer t) {
        TransferItemDTO dto = new TransferItemDTO();
        dto.setId(t.getId());
        dto.setProcessInstanceId(t.getProcessInstanceId());
        dto.setTaskId(t.getTaskId());
        dto.setBusinessKey(t.getBusinessKey());
        dto.setBizType(t.getBizType());
        dto.setNodeKey(t.getNodeKey());
        dto.setNodeName(t.getNodeName());
        dto.setFromEmpId(t.getFromEmpId());
        dto.setInitiatorEmpId(t.getInitiatorEmpId());
        dto.setToEmpId(t.getToEmpId());
        dto.setOrgCode(t.getOrgCode());
        dto.setStatus(t.getStatus());
        dto.setTransferReason(t.getTransferReason());
        dto.setRejectReason(t.getRejectReason());
        dto.setInitiatedTime(t.getInitiatedTime());
        dto.setDecidedTime(t.getDecidedTime());
        return dto;
    }

    /**
     * 接收人认领转交（两阶段转交的第二阶段）：将办理权真正转移给接收人。
     * <p>
     * 校验链（任一不通过即抛异常，不写库）：
     * <ol>
     *   <li>转交记录存在且仍为 PENDING_ACCEPT（WF-40914，覆盖"不存在"和"已处理"两种情形）</li>
     *   <li>当前登录用户即接收人（WF-40303，只有接收人本人可认领）</li>
     * </ol>
     * 校验通过后先做乐观状态流转（{@code updateStatusIfPending}），若并发下已被其他请求（如发起人撤销、
     * 接收人在另一端重复点击）抢先流转，返回 0 → 抛 WF-40915 冲突异常，<b>不再</b>推进到 setAssignee，
     * 避免并发败者仍然改写 Flowable assignee 造成状态不一致。
     * </p>
     * <p>
     * 乐观更新命中后依次：改 Flowable 任务 assignee → 同步 {@code biz_process_map.current_assignee}
     * （查不到映射不阻断）→ 记参与机构快照（source=TRANSFER）→ 任务加认领评论留痕 → 通知发起人。
     * </p>
     *
     * @param transferId 转交记录ID
     * @throws BizException WF-40914 转交不存在或已处理；WF-40303 非接收人；WF-40915 并发状态已变更
     */
    @Transactional
    public void accept(String transferId) {
        String me = currentUserApi.getCurrentEmpId();
        WfTaskTransfer t = requirePending(transferId);
        if (!me.equals(t.getToEmpId())) {
            throw new BizException(WfErrorCode.TRANSFER_NOT_RECEIVER.getCode(),
                    WfErrorCode.TRANSFER_NOT_RECEIVER.getMessage());
        }

        // 乐观流转：并发败者（返回0）在此拦截，绝不能推进到下面的 setAssignee
        int n = wfTaskTransferMapper.updateStatusIfPending(transferId, STATUS_ACCEPTED, null, LocalDateTime.now());
        if (n == 0) {
            throw new BizException(WfErrorCode.TRANSFER_STATE_CHANGED.getCode(),
                    WfErrorCode.TRANSFER_STATE_CHANGED.getMessage());
        }

        taskService.setAssignee(t.getTaskId(), me);

        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(t.getProcessInstanceId());
        if (map != null) {
            map.setCurrentAssignee(me);
            bizProcessMapMapper.updateById(map);
        }

        wfProcessOrgService.record(t.getProcessInstanceId(), me, "TRANSFER");
        taskService.addComment(t.getTaskId(), t.getProcessInstanceId(), "TRANSFER_ACCEPTED", null);

        notifyApi.sendNotification(NotificationCmd.builder()
                .targetEmpId(t.getInitiatorEmpId())
                .title("转交已被认领")
                .content("任务[" + t.getNodeName() + "]已被" + me + "认领")
                .notifyType("WORKFLOW")
                .bizType(t.getBizType())
                .bizId(t.getBusinessKey())
                .build());

        log.info("转交认领: transferId={}, taskId={}, to={}, initiator={}",
                transferId, t.getTaskId(), me, t.getInitiatorEmpId());
    }

    /**
     * 接收人拒绝转交（两阶段转交的另一终态分支）。
     * <p>
     * 校验链（任一不通过即抛异常，不写库）：
     * <ol>
     *   <li>转交记录存在且仍为 PENDING_ACCEPT（WF-40914，与 {@link #accept} 共用同一加载校验）</li>
     *   <li>当前登录用户即接收人（WF-40304，只有接收人本人可拒绝）</li>
     *   <li>拒绝理由非空（WF-40001）——Controller 层（Task 12）已用 {@code @Valid}
     *       挡在最外层，这里是服务层防御性兜底，防止未来出现绕过 Controller 直接调用
     *       Service 的调用路径</li>
     * </ol>
     * 校验通过后先做乐观状态流转（{@code updateStatusIfPending}），命中后依次：任务加拒绝评论留痕
     * （含理由）→ 同时通知发起人与原办理人（{@code fromEmpId}），两者都需要知悉转交未被接收人认领。
     * </p>
     * <p>
     * <b>解锁天然发生</b>：转交记录状态一旦流出 PENDING_ACCEPT，{@code selectActiveByTaskId}
     * 即查不到该任务的生效转交——原办理人的"转交待认领只读锁"（Task 9）随之解除，无需额外解锁代码。
     * </p>
     *
     * @param transferId 转交记录ID
     * @param reason     拒绝理由（必填）
     * @throws BizException WF-40914 转交不存在或已处理；WF-40304 非接收人；
     *                       WF-40001 理由为空；WF-40915 并发状态已变更
     */
    @Transactional
    public void decline(String transferId, String reason) {
        String me = currentUserApi.getCurrentEmpId();
        WfTaskTransfer t = requirePending(transferId);
        if (!me.equals(t.getToEmpId())) {
            throw new BizException(WfErrorCode.TRANSFER_DECLINE_NOT_RECEIVER.getCode(),
                    WfErrorCode.TRANSFER_DECLINE_NOT_RECEIVER.getMessage());
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(WfErrorCode.TRANSFER_REJECT_REASON_REQUIRED.getCode(),
                    WfErrorCode.TRANSFER_REJECT_REASON_REQUIRED.getMessage());
        }

        // 乐观流转：并发败者（返回0）在此拦截，绝不能推进到下面的加评论/通知
        int n = wfTaskTransferMapper.updateStatusIfPending(transferId, STATUS_REJECTED, reason, LocalDateTime.now());
        if (n == 0) {
            throw new BizException(WfErrorCode.TRANSFER_STATE_CHANGED.getCode(),
                    WfErrorCode.TRANSFER_STATE_CHANGED.getMessage());
        }

        taskService.addComment(t.getTaskId(), t.getProcessInstanceId(), "TRANSFER_REJECTED", reason);
        notifyInitiatorAndFrom(t, "转交被拒绝", "任务[" + t.getNodeName() + "]转交被接收人拒绝，理由：" + reason);

        log.info("转交拒绝: transferId={}, taskId={}, receiver={}, initiator={}, from={}",
                transferId, t.getTaskId(), me, t.getInitiatorEmpId(), t.getFromEmpId());
    }

    /**
     * 发起人撤回转交（两阶段转交的第三终态分支）。
     * <p>
     * 校验链（任一不通过即抛异常，不写库）：
     * <ol>
     *   <li>转交记录存在且仍为 PENDING_ACCEPT（WF-40914，与 {@link #accept} 共用同一加载校验）</li>
     *   <li>当前登录用户即发起人（WF-40305，只有发起人本人可撤回；秘书代发起场景下，
     *       撤回权限归属发起人 {@code initiatorEmpId} 而非原办理人 {@code fromEmpId}）</li>
     * </ol>
     * 校验通过后乐观流转为 CANCELLED（{@code rejectReason} 传 null），命中后通知接收人。
     * 转交记录状态流出 PENDING_ACCEPT 后 {@code selectActiveByTaskId} 即查不到该任务的生效转交，
     * 原办理人的转交待认领只读锁（Task 9）天然解除，无需额外解锁代码。
     * </p>
     *
     * @param transferId 转交记录ID
     * @throws BizException WF-40914 转交不存在或已处理；WF-40305 非发起人；WF-40915 并发状态已变更
     */
    @Transactional
    public void cancel(String transferId) {
        String me = currentUserApi.getCurrentEmpId();
        WfTaskTransfer t = requirePending(transferId);
        if (!me.equals(t.getInitiatorEmpId())) {
            throw new BizException(WfErrorCode.TRANSFER_CANCEL_NOT_INITIATOR.getCode(),
                    WfErrorCode.TRANSFER_CANCEL_NOT_INITIATOR.getMessage());
        }

        // 乐观流转：并发败者（返回0）在此拦截，绝不能推进到下面的通知
        int n = wfTaskTransferMapper.updateStatusIfPending(transferId, STATUS_CANCELLED, null, LocalDateTime.now());
        if (n == 0) {
            throw new BizException(WfErrorCode.TRANSFER_STATE_CHANGED.getCode(),
                    WfErrorCode.TRANSFER_STATE_CHANGED.getMessage());
        }

        notifyApi.sendNotification(NotificationCmd.builder()
                .targetEmpId(t.getToEmpId())
                .title("转交已撤回")
                .content("任务[" + t.getNodeName() + "]转交被发起人撤回")
                .notifyType("WORKFLOW")
                .bizType(t.getBizType())
                .bizId(t.getBusinessKey())
                .build());

        log.info("转交撤回: transferId={}, taskId={}, initiator={}, receiver={}",
                transferId, t.getTaskId(), me, t.getToEmpId());
    }

    /**
     * 加载待认领转交记录，须存在且状态仍为 PENDING_ACCEPT，否则统一按 WF-40914（转交不存在或已处理）
     * 拒绝——不区分"记录根本不存在"与"已被处理过（认领/拒绝/撤回）"两种情形，避免向调用方泄漏
     * 转交记录的存在性/历史状态信息。{@link #accept}/{@link #decline}/{@link #cancel} 共用。
     */
    private WfTaskTransfer requirePending(String transferId) {
        WfTaskTransfer t = wfTaskTransferMapper.selectById(transferId);
        if (t == null || !STATUS_PENDING_ACCEPT.equals(t.getStatus())) {
            throw new BizException(WfErrorCode.TRANSFER_NOT_FOUND_OR_PROCESSED.getCode(),
                    WfErrorCode.TRANSFER_NOT_FOUND_OR_PROCESSED.getMessage());
        }
        return t;
    }

    /**
     * 拒绝场景下同时通知发起人与原办理人（{@code fromEmpId}）：两者都需要知悉转交未被接收人认领，
     * 任务仍停留在原办理人手中（转交锁已随状态流转解除）。
     */
    private void notifyInitiatorAndFrom(WfTaskTransfer t, String title, String content) {
        notifyApi.sendNotification(NotificationCmd.builder()
                .targetEmpId(t.getInitiatorEmpId())
                .title(title)
                .content(content)
                .notifyType("WORKFLOW")
                .bizType(t.getBizType())
                .bizId(t.getBusinessKey())
                .build());
        notifyApi.sendNotification(NotificationCmd.builder()
                .targetEmpId(t.getFromEmpId())
                .title(title)
                .content(content)
                .notifyType("WORKFLOW")
                .bizType(t.getBizType())
                .bizId(t.getBusinessKey())
                .build());
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
     * 见 {@link CandidateResolverService#resolveCandidates}）。以「任务实际身份链接」为权威依据，
     * 只有该任务完全没有 candidate 身份链接时才回退到节点原始配置：
     * </p>
     * <ol>
     *   <li><b>任务实际身份链接</b>（{@code taskService.getIdentityLinksForTask}，type=candidate）
     *       非空 → <b>只</b>按这份权威名单判定：接收人 userId 直接匹配，或 groupId 命中接收人的
     *       候选组标识（{@link UserApi#getCandidateGroupKeys}，ROLE:/USER:/ORG: 前缀口径）。
     *       <b>不再</b>额外 OR 上节点原始配置——{@code TaskAssignmentListener} 在任务创建时可能按
     *       发起机构把候选收窄为具体机构下的用户（如 branch_approve 系列节点 {@code addCandidateUser}），
     *       若仍拿未过滤的原始 {@code nodeCandidates} 去交集，会放行一个同角色但不在收窄范围内、
     *       实际根本不是该任务候选人的接收人（越权绕过）。</li>
     *   <li><b>节点配置候选</b>（{@code nodeCandidates}，未经机构过滤的原始配置）：仅当任务
     *       <b>完全没有</b> candidate 类型身份链接时才使用（例如纯直接指派节点，从未被
     *       机构收窄过，不存在"权威名单被绕过"的风险），判定口径同上（USER: 精确匹配 或
     *       receiverKeys 交集）。</li>
     * </ol>
     */
    private boolean isEligibleReceiver(String taskId, List<String> nodeCandidates, String toEmpId) {
        Set<String> receiverKeys = userApi.getCandidateGroupKeys(toEmpId);

        List<IdentityLink> candidateLinks = extractCandidateLinks(taskId);
        if (!candidateLinks.isEmpty()) {
            for (IdentityLink link : candidateLinks) {
                if (toEmpId.equals(link.getUserId())) {
                    return true;
                }
                if (link.getGroupId() != null && receiverKeys.contains(link.getGroupId())) {
                    return true;
                }
            }
            return false;
        }

        // 任务完全没有 candidate 身份链接（未被机构收窄过，如纯直接指派节点）→ 回退节点原始配置
        return candidateContains(nodeCandidates, receiverKeys, toEmpId);
    }

    /** 取任务上 type=candidate 的身份链接列表（Flowable {@code getIdentityLinksForTask} 可能返回 null）。 */
    private List<IdentityLink> extractCandidateLinks(String taskId) {
        List<IdentityLink> links = taskService.getIdentityLinksForTask(taskId);
        if (links == null) {
            return List.of();
        }
        return links.stream()
                .filter(link -> IdentityLinkType.CANDIDATE.equals(link.getType()))
                .toList();
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
