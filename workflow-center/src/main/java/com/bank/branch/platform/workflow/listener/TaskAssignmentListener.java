package com.bank.branch.platform.workflow.listener;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import com.bank.branch.platform.workflow.service.CandidateResolverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
    private final com.bank.branch.platform.workflow.mapper.BizProcessMapMapper bizProcessMapMapper;

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
        // Flowable 7 默认使用 UUID 作 processDefinitionId（无 ":" 分隔），不能 split(":")[0]。
        // 走 RepositoryService 反查 ProcessDefinition.getKey() 拿真实 BPMN KEY。
        String processDefinitionId = delegateTask.getProcessDefinitionId();
        ProcessDefinition pd = repositoryService.getProcessDefinition(processDefinitionId);
        String processDefinitionKey = pd != null ? pd.getKey() : processDefinitionId;
        String nodeKey = delegateTask.getTaskDefinitionKey();
        String taskId = delegateTask.getId();

        // 解析候选组
        List<String> candidates = candidateResolverService.resolveCandidates(processDefinitionKey, nodeKey);

        // branch_approve 节点：按"审批机构"过滤候选，只让该机构的经营机构负责人(分行/支行负责人)审批。
        // 审批机构按发起人机构等级决定：等级3(支行)→上级机构(等级2,分行)；等级2→本机构。
        if ("branch_approve".equals(nodeKey)) {
            Object startOrgId = delegateTask.getVariable("startOrgId");
            if (startOrgId != null && !startOrgId.toString().isEmpty()) {
                String approveOrg = resolveBranchApproveOrg(startOrgId.toString());
                filterCandidatesByOrg(delegateTask, candidates, approveOrg);
                log.info("[TaskAssignmentListener] 任务 {} branch_approve 发起机构 {} → 审批机构 {} 过滤候选",
                        taskId, startOrgId, approveOrg);
                notifyCandidates(delegateTask, candidates);
                return;
            }
        }

        // 其他节点：正常设置候选组
        for (String group : candidates) {
            delegateTask.addCandidateGroup(group);
        }

        log.info("[TaskAssignmentListener] 任务 {} 已设置候选组 {}", taskId, candidates);

        // 尝试发送通知，失败不影响流程
        // candidates 含前缀（USER:E001 / ROLE:BRANCH_HEAD / ORG:O123），需展开成真实 empId 列表
        if (!candidates.isEmpty()) {
            try {
                Set<String> empIds = expandCandidatesToEmpIds(candidates);
                if (empIds.isEmpty()) {
                    log.info("[TaskAssignmentListener] 任务 {} 候选展开后无员工，不发通知 candidates={}", taskId, candidates);
                    return;
                }
                // 从 BizProcessMap 拿具体业务信息，让通知标题和类型更明确
                String processInstanceId = delegateTask.getProcessInstanceId();
                com.bank.branch.platform.workflow.entity.BizProcessMap bizMap =
                        bizProcessMapMapper.selectByProcessInstanceId(processInstanceId);
                String bizType = bizMap != null ? bizMap.getBizType() : null;
                String bizId = bizMap != null ? bizMap.getBizId() : null;
                // 目标修正：按业务要求，待审批任务只进「待办」列表，不发通知消息；
                // 仅审批通过/驳回时由 TargetAdjustCompletedListener 通知申请人。
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
    }

    /**
     * 机构负责人审批环节的审批机构解析：
     * <ul>
     *   <li>发起人机构等级 = 3（支行）→ 由上级机构（等级 2，分行）的负责人审批 → 取 parentOrgCode</li>
     *   <li>发起人机构等级 = 2 → 由本机构负责人审批 → 取发起人机构</li>
     *   <li>其它等级 / 查不到机构 / 无上级 → 兜底用发起人本机构（保持原行为）</li>
     * </ul>
     */
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
            Set<String> empIds = expandCandidatesToEmpIds(candidates);
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
    private Set<String> expandCandidatesToEmpIds(List<String> candidates) {
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
