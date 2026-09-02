package com.bank.branch.platform.workflow.listener;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import com.bank.branch.platform.workflow.api.dto.ApproverGroupDTO;
import com.bank.branch.platform.workflow.service.CandidateResolverService;
import com.bank.branch.platform.workflow.service.WfProcessOrgService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务分配监听器。
 * <p>
 * 在 Flowable 用户任务创建时触发，根据流程定义KEY和节点KEY
 * 解析候选人配置并设置到任务的候选组上。
 * 同时尝试通过 NotifyApi 发送通知给候选人，通知失败不影响流程继续。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskAssignmentListener implements TaskListener {

    private final CandidateResolverService candidateResolverService;
    private final NotifyApi notifyApi;
    private final RepositoryService repositoryService;
    private final UserApi userApi;
    private final OrgApi orgApi;
    private final TaskService taskService;
    private final com.bank.branch.platform.workflow.mapper.BizProcessMapMapper bizProcessMapMapper;
    /**
     * 参与机构快照写入唯一入口。两条挂点：任务已有具体受理人时按受理人记（source=ASSIGN）；
     * 候选人模式（受理人为 NULL）下按解析出的审批机构记（source=CANDIDATE）——后者是为了让
     * 审批流监控页在无人签收前也能按机构范围查到「正等本机构审批」的流程。
     * 「不限机构」的候选组没有确定的审批机构，仍不记。
     */
    private final WfProcessOrgService wfProcessOrgService;

    /** 「二级机构」(L2 scope) 上溯目标：机构等级 2（分行）。层级角色选「二级机构」时沿 P_ID 上溯到该级。 */
    private static final int SECOND_ORG_LEVEL = 2;

    /**
     * 任务创建事件回调。
     * <p>
     * 1. 从 delegateTask 中提取流程定义KEY和节点KEY
     * 2. 通过 CandidateResolverService 解析候选组列表
     * 3. 将候选组设置到 delegateTask 上
     * 4. 尝试发送通知（异常不中断流程）
     * </p>
     *
     * @param delegateTask Flowable 委托任务对象
     */
    @Override
    public void notify(DelegateTask delegateTask) {
        // 记录参与机构快照（D5：任务创建时若已解析出具体受理人，即非仅候选组，source=ASSIGN）
        if (delegateTask.getAssignee() != null) {
            wfProcessOrgService.record(delegateTask.getProcessInstanceId(),
                    delegateTask.getAssignee(), "ASSIGN");
        }

        // Flowable 7 默认使用 UUID 作 processDefinitionId（无 ":" 分隔），不能 split(":")[0]。
        // 走 RepositoryService 反查 ProcessDefinition.getKey() 拿真实 BPMN KEY。
        String processDefinitionId = delegateTask.getProcessDefinitionId();
        ProcessDefinition pd = repositoryService.getProcessDefinition(processDefinitionId);
        String processDefinitionKey = pd != null ? pd.getKey() : processDefinitionId;
        String nodeKey = delegateTask.getTaskDefinitionKey();
        String taskId = delegateTask.getId();

        // GROUP_ALL 顺序多实例的当前元素是一个机构审批组。组内负责人共享同一个 Flowable
        // userTask，任一候选人完成即结束本组；不要再按设计器候选配置做扁平化解析，避免
        // 把不同机构合并成一个 ANY 节点。
        if (delegateTask.hasVariable(MultiInstanceApproverGroupResolver.VAR_APPROVER_GROUP)) {
            Object groupValue = delegateTask.getVariable(MultiInstanceApproverGroupResolver.VAR_APPROVER_GROUP);
            if (groupValue == null) {
                throw new IllegalStateException("机构审批分组配置无效: 当前任务缺少机构审批分组元素");
            }
            assignApproverGroup(delegateTask, groupValue, taskId, nodeKey);
            return;
        }

        // 虚拟员工默认通过（泛化）：任意审批任务的受理人若为「虚拟员工」(字典 USER_TYPE=2)，
        // 无需人工审批，自动默认审批通过，审批意见记「默认同意」，流程继续到后续节点。
        // （原仅限 original_owner_approve 节点，现去掉 nodeKey 限制，对设计器重建的任意流程一致生效。）
        if (autoApproveIfVirtualOwner(delegateTask, taskId)) {
            return;
        }

        // 解析候选组（前缀化标识列表，VAR/ROLE/ORG/USER）
        List<String> candidates = candidateResolverService.resolveCandidates(processDefinitionKey, nodeKey);
        // 每个审批人各自的「审批机构归属」（SELF/PARENT）；未配置机构归属的审批人不在此 map 中。
        // 逐行 scope 是为了支持同一节点混合机构归属：例如 branch_approve_l3 既有 PARENT(上级机构)的
        // 机构负责人，又有「不限机构」的公司业绩预审角色——后者必须整组作为候选组，不能被前者的
        // PARENT 误过滤掉（否则该角色用户在工作台看不到待办）。
        Map<String, String> scopeByCandidate =
                candidateResolverService.resolveCandidateScopeMap(processDefinitionKey, nodeKey);
        // 历史静态 branch_approve 节点（整节点无任何显式机构归属）→ 按发起人机构等级自动解析
        boolean legacyBranchApprove = "branch_approve".equals(nodeKey) && scopeByCandidate.isEmpty();
        Object startOrgIdVar = delegateTask.getVariable("startOrgId");
        String startOrgId = startOrgIdVar != null ? startOrgIdVar.toString() : null;
        boolean hasStartOrg = startOrgId != null && !startOrgId.isEmpty();

        // 逐个候选人按各自机构归属分派：
        //  - VAR:变量名               → 从流程变量取审批人，设为候选用户（不做机构过滤）
        //  - 配 SELF/PARENT(或历史 branch_approve)且有发起机构 → 按对应机构过滤该角色成员为候选用户
        //  - 其余(不限机构)           → 整组角色/机构/用户作为候选组（该角色全部成员可见）
        for (String c : candidates) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            if (c.startsWith("VAR:")) {
                Object varValue = delegateTask.getVariable(c.substring(4));
                for (String empId : MultiInstanceApproverResolver.readVarEmpIds(varValue)) {
                    delegateTask.addCandidateUser(empId);
                }
                continue;
            }
            String scope = scopeByCandidate.get(c);
            if ((scope != null || legacyBranchApprove) && hasStartOrg) {
                String approveOrg = resolveApproveOrg(startOrgId, scope, legacyBranchApprove);
                filterCandidatesByOrg(delegateTask, java.util.List.of(c), approveOrg);
                // 候选人模式（assignee 为 NULL）也要落参与机构快照：审批机构此刻已确定，
                // 若等到有人签收才记，该机构的秘书/行长在审批流监控页看不到"正等我们行审批"的流程。
                wfProcessOrgService.recordOrg(delegateTask.getProcessInstanceId(), approveOrg, "CANDIDATE");
                log.info("[TaskAssignmentListener] 任务 {} 节点 {} 候选 {} 机构归属 {} 发起机构 {} → 审批机构 {}",
                        taskId, nodeKey, c, scope != null ? scope : "AUTO", startOrgId, approveOrg);
            } else {
                delegateTask.addCandidateGroup(c);
            }
        }
        log.info("[TaskAssignmentListener] 任务 {} 节点 {} 候选分派完成 candidates={} 机构归属={}",
                taskId, nodeKey, candidates, scopeByCandidate);

        // 待审批任务统一只进「待办」、不发通知（isNotifySuppressed 恒为 true），复用 notifyCandidates 收口
        notifyCandidates(delegateTask, candidates);
    }

    /**
     * 为当前机构审批组设置候选人、任务展示名称和参与机构快照。
     *
     * @param delegateTask 当前机构组任务
     * @param groupValue Flowable 多实例元素变量（DTO 或可转换的 Map）
     * @param taskId 任务 ID
     * @param nodeKey 节点 KEY
     */
    private void assignApproverGroup(DelegateTask delegateTask, Object groupValue,
                                     String taskId, String nodeKey) {
        ApproverGroupDTO group = MultiInstanceApproverGroupResolver.readGroup(groupValue);
        for (String empId : group.getApproverEmpIds()) {
            delegateTask.addCandidateUser(empId);
        }
        String baseName = delegateTask.getName();
        String taskName = (baseName == null || baseName.isBlank())
                ? group.getGroupName()
                : baseName + "（" + group.getGroupName() + "）";
        delegateTask.setName(taskName);
        wfProcessOrgService.recordOrg(delegateTask.getProcessInstanceId(), group.getGroupKey(), "CANDIDATE");
        log.info("[TaskAssignmentListener] 任务 {} 节点 {} 按机构组 {} 分派候选人 {}，任务名={}",
                taskId, nodeKey, group.getGroupKey(), group.getApproverEmpIds(), taskName);
    }

    /**
     * 原业绩所属人审批：若原业绩所属人为虚拟员工(USER_TYPE=2)，自动以「默认同意」完成任务。
     *
     * <p>原业绩所属人取任务受理人（BPMN assignee=${ownerEmpId}），兜底取流程变量 ownerEmpId。
     * 完成失败（如引擎并发）则吞异常返回 false，退回人工审批，保证流程不中断。
     *
     * @return true 表示已自动审批通过并完成任务（调用方应直接 return）；false 表示需走人工审批
     */
    private boolean autoApproveIfVirtualOwner(DelegateTask delegateTask, String taskId) {
        // 仅当任务有具体受理人时才判断（会签多实例下为当前实例审批人 ${approver}）；
        // 候选组节点（无 assignee）不存在"虚拟员工"概念，直接退回人工。
        String assignee = delegateTask.getAssignee();
        if (assignee == null || assignee.isEmpty()) {
            return false;
        }
        try {
            UserDTO owner = userApi.getUserByEmpId(assignee);
            if (owner != null && "2".equals(owner.getUserType())) {
                taskService.addComment(taskId, delegateTask.getProcessInstanceId(), "APPROVE", "默认同意");
                taskService.complete(taskId, java.util.Map.of("approved", true));
                log.info("[TaskAssignmentListener] 任务 {} 受理人 {} 为虚拟员工，自动默认同意并完成", taskId, assignee);
                return true;
            }
        } catch (Exception e) {
            log.warn("[TaskAssignmentListener] 虚拟员工自动审批失败 assignee={}，转人工审批，原因 {}",
                    assignee, e.getMessage());
        }
        return false;
    }

    /**
     * 机构负责人审批环节的审批机构解析：
     * <ul>
     *   <li>发起人机构等级 = 3（支行）→ 由上级机构（等级 2，分行）的负责人审批 → 取 parentOrgCode</li>
     *   <li>发起人机构等级 = 2 → 由本机构负责人审批 → 取发起人机构</li>
     *   <li>其它等级 / 查不到机构 / 无上级 → 兜底用发起人本机构（保持原行为）</li>
     * </ul>
     */
    /**
     * 按节点「审批机构归属」配置解析审批机构编码：
     * <ul>
     *   <li>SELF   → 本机构：返回发起人机构编码（审批人机构号 = 发起人机构号）</li>
     *   <li>PARENT → 上级机构：返回发起人机构的 parentOrgCode（无上级则兜底本机构）</li>
     *   <li>L2     → 二级机构：沿 P_ID 上溯到机构等级 2（分行），四级网点/三级支行发起均落到所属分行；
     *               发起机构本身已 ≤2 级或断链则兜底本机构（见 {@link #resolveSecondLevelOrg}）</li>
     *   <li>legacyAuto（历史 branch_approve 无显式配置）→ 按机构等级自动解析（3级→上级，否则本机构）</li>
     * </ul>
     *
     * @param startOrgCode 发起人机构编码
     * @param scope        SELF / PARENT / L2 / null
     * @param legacyAuto   是否走历史 branch_approve 等级自动解析
     * @return 审批机构编码
     */
    private String resolveApproveOrg(String startOrgCode, String scope, boolean legacyAuto) {
        try {
            if ("SELF".equals(scope)) {
                return startOrgCode;
            }
            if ("PARENT".equals(scope)) {
                OrgDTO org = orgApi.getOrg(startOrgCode);
                if (org != null && org.getParentOrgCode() != null && !org.getParentOrgCode().isBlank()) {
                    return org.getParentOrgCode();
                }
                return startOrgCode;  // 无上级兜底本机构
            }
            if ("L2".equals(scope)) {
                return resolveSecondLevelOrg(startOrgCode);
            }
            // AUTO：按发起人机构层级自动解析（3级支行→上级分行 / 2级→本机构），与历史 branch_approve 行为一致
            if ("AUTO".equals(scope) || legacyAuto) {
                return resolveBranchApproveOrg(startOrgCode);
            }
        } catch (Exception e) {
            log.warn("[TaskAssignmentListener] 解析审批机构失败 startOrg={}, scope={}，兜底本机构，原因 {}",
                    startOrgCode, scope, e.getMessage());
        }
        return startOrgCode;
    }

    private String resolveBranchApproveOrg(String startOrgCode) {
        try {
            OrgDTO org = orgApi.getOrg(startOrgCode);
            if (org != null && org.getOrgLevel() != null && org.getOrgLevel() == 3
                    && org.getParentOrgCode() != null && !org.getParentOrgCode().isBlank()) {
                log.info("[TaskAssignmentListener] branch_approve 三级机构 {} → 上级机构 {} 审批",
                        startOrgCode, org.getParentOrgCode());
                return org.getParentOrgCode();
            }
        } catch (Exception e) {
            log.warn("[TaskAssignmentListener] 解析审批机构失败 startOrg={}，兜底本机构，原因 {}",
                    startOrgCode, e.getMessage());
        }
        return startOrgCode;
    }

    /**
     * 「二级机构」(L2) 解析：从发起机构沿 P_ID 上级链上溯，返回机构等级 = {@link #SECOND_ORG_LEVEL}（分行）的上级机构编码。
     * <p>业务背景：实际机构树最深达 4 级（总行 1 / 分行 2 / 支行 3 / 网点 4）。层级角色选「发起上级机构」(PARENT)
     * 只跳一级——四级网点发起时只能到三级支行；选「二级机构」则固定上溯到所属分行，让机构负责人审批稳定落到分行。
     * <p>兜底：发起机构本身已是 2 级 → 即本机构；已 ≤2 级但非 2 级（如总行）或上溯断链/成环 → 返回发起人本机构，
     * 绝不放空，避免流程行至该节点无人可批。hops 上限防脏数据成环（与 AllocAdjustService 同口径）。
     *
     * @param startOrgCode 发起人机构编码
     * @return 上溯到的 2 级机构编码；无 2 级上级时兜底为发起人本机构
     */
    private String resolveSecondLevelOrg(String startOrgCode) {
        try {
            String code = startOrgCode;
            OrgDTO org = orgApi.getOrg(code);
            int hops = 0;
            // 沿 P_ID 上溯，直到命中 2 级机构；等级缺失也继续上溯
            while (org != null && (org.getOrgLevel() == null || org.getOrgLevel() > SECOND_ORG_LEVEL)) {
                String parent = org.getParentOrgCode();
                if (++hops > 10 || parent == null || parent.isBlank()) {
                    return startOrgCode;  // 断链/成环兜底本机构
                }
                code = parent;
                org = orgApi.getOrg(code);
            }
            if (org != null && org.getOrgLevel() != null && org.getOrgLevel() == SECOND_ORG_LEVEL) {
                log.info("[TaskAssignmentListener] 二级机构解析 发起机构 {} → 所属分行 {} 审批", startOrgCode, code);
                return code;
            }
        } catch (Exception e) {
            log.warn("[TaskAssignmentListener] 解析二级机构失败 startOrg={}，兜底本机构，原因 {}",
                    startOrgCode, e.getMessage());
        }
        return startOrgCode;  // 无 2 级上级（如总行发起）兜底本机构
    }

    /**
     * branch_approve 节点专用：按机构过滤候选人，直接指派同机构员工为候选用户。
     * 将 ROLE:XXX 候选展开后按 orgCode 过滤，设为任务的候选用户（非候选组）。
     */
    private void filterCandidatesByOrg(DelegateTask delegateTask, List<String> candidates, String orgCode) {
        Set<String> filteredEmpIds = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) continue;
            int colon = candidate.indexOf(':');
            String type = colon > 0 ? candidate.substring(0, colon) : "";
            String value = colon > 0 ? candidate.substring(colon + 1) : candidate;
            if ("ROLE".equals(type)) {
                List<String> orgEmps = userApi.getEmpIdsByRoleCodeAndOrg(value, orgCode);
                if (orgEmps != null) filteredEmpIds.addAll(orgEmps);
            } else if ("USER".equals(type)) {
                filteredEmpIds.add(value);
            }
        }
        for (String empId : filteredEmpIds) {
            delegateTask.addCandidateUser(empId);
        }
        log.info("[TaskAssignmentListener] branch_approve 机构过滤后候选用户: {}", filteredEmpIds);
    }

    /**
     * 发送通知给候选人（抽取公共方法，branch_approve 路径和普通路径共用）。
     */
    private void notifyCandidates(DelegateTask delegateTask, List<String> candidates) {
        String taskId = delegateTask.getId();
        if (candidates.isEmpty()) return;
        try {
            Set<String> empIds = expandCandidatesToEmpIds(candidates, delegateTask);
            if (empIds.isEmpty()) return;
            String processInstanceId = delegateTask.getProcessInstanceId();
            com.bank.branch.platform.workflow.entity.BizProcessMap bizMap =
                    bizProcessMapMapper.selectByProcessInstanceId(processInstanceId);
            String bizType = bizMap != null ? bizMap.getBizType() : null;
            String bizId = bizMap != null ? bizMap.getBizId() : null;
            if (isNotifySuppressed(bizType)) {
                log.info("[TaskAssignmentListener] 任务 {} bizType={} 跳过待审批通知（仅进待办）", taskId, bizType);
                return;
            }
            String taskName = delegateTask.getName();
            String bizLabel = resolveBizLabel(bizType);
            String title = bizLabel != null
                    ? "待办：" + bizLabel + (taskName != null ? " · " + taskName : "")
                    : "您有新的待办任务";
            String content = bizLabel != null
                    ? "您有一条【" + bizLabel + "】待办" + (taskName != null ? "（" + taskName + "）" : "") + "，请及时处理"
                    : "您有新待办任务，请及时处理";
            List<NotificationCmd> cmds = empIds.stream()
                    .map(empId -> NotificationCmd.builder()
                            .targetEmpId(empId)
                            .title(title)
                            .content(content)
                            .notifyType("WORKFLOW")
                            .bizType(bizType)
                            .bizId(bizId)
                            .build())
                    .collect(Collectors.toList());
            notifyApi.batchSendNotifications(cmds);
            log.info("[TaskAssignmentListener] 任务 {} 已通知 {} 个员工", taskId, empIds.size());
        } catch (Exception e) {
            log.warn("[TaskAssignmentListener] 发送通知失败，任务 {}，原因: {}", taskId, e.getMessage());
        }
    }

    /**
     * 把带前缀的候选列表展开成员工 ID 集合（去重）。
     * <p>
     * - USER:E001 → 直接拿 E001
     * - ROLE:BRANCH_HEAD → 调 UserApi.getEmpIdsByRoleCode 展开
     * - ORG:O123 → 暂不支持（auth 模块未提供按 org 查员工 API），log warn 跳过
     * </p>
     */
    private Set<String> expandCandidatesToEmpIds(List<String> candidates, DelegateTask delegateTask) {
        Set<String> empIds = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) continue;
            int colon = candidate.indexOf(':');
            String type = colon > 0 ? candidate.substring(0, colon) : "";
            String value = colon > 0 ? candidate.substring(colon + 1) : candidate;
            switch (type) {
                case "USER" -> empIds.add(value);
                case "ROLE" -> {
                    List<String> roleEmps = userApi.getEmpIdsByRoleCode(value);
                    if (roleEmps != null) empIds.addAll(roleEmps);
                }
                // VAR：从提交方传入的流程变量取审批人 empId
                case "VAR" -> empIds.addAll(MultiInstanceApproverResolver.readVarEmpIds(delegateTask.getVariable(value)));
                case "ORG" -> log.warn("[TaskAssignmentListener] ORG 类型候选暂不支持展开，跳过 candidate={}", candidate);
                default -> {
                    // 无前缀 fallback 当 empId
                    empIds.add(value);
                }
            }
        }
        return empIds;
    }

    /**
     * 是否抑制该 bizType 的待审批任务通知（仅进待办列表，不发通知消息）。
     * <p>业务要求：所有待审批任务只进「待办」列表，不再发"待办"通知消息，
     * 避免工作台「通知」模块混入待办信息。审批结果（通过/驳回）仍由各
     * *CompletedListener 单独通知申请人，不受此影响。
     * <p>历史上仅 TARGET_ADJUST 被抑制，现统一对所有 bizType 抑制。
     */
    private boolean isNotifySuppressed(String bizType) {
        return true;
    }

    /** BIZ_TYPE → 中文标签，让通知标题更可读 */
    private String resolveBizLabel(String bizType) {
        if (bizType == null) return null;
        return switch (bizType) {
            case "ALLOC_ADJUST" -> "业绩调整审批";
            case "TARGET_ADJUST" -> "目标修正审批";
            case "LOAN_APPLY" -> "贷款申请审批";
            default -> bizType;
        };
    }
}
