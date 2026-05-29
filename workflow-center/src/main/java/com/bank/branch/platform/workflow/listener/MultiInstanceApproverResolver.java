package com.bank.branch.platform.workflow.listener;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.workflow.service.CandidateResolverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 会签（Multi-Instance）审批节点入口监听器。
 * <p>
 * 在 MI userTask 的 executionListener（event="start"）触发时，
 * 按 (processDefinitionKey, nodeKey) 从 {@link CandidateResolverService} 读取
 * 带前缀的候选规则，展开成员工 ID 集合，并 set 到流程变量 {@code approverEmpIds}。
 * MI userTask 以 {@code collection="approverEmpIds"}、
 * {@code elementVariable="approver"} 引用该变量。
 * </p>
 *
 * <p>展开规则（与 {@link TaskAssignmentListener} 保持一致）：</p>
 * <ul>
 *   <li>{@code USER:E001}  → 直接取 E001</li>
 *   <li>{@code ROLE:CODE}  → 调 {@link UserApi#getEmpIdsByRoleCode(String)} 展开</li>
 *   <li>{@code ORG:xxx}   → 暂不支持，log warn 跳过</li>
 *   <li>无前缀            → fallback 当 empId</li>
 * </ul>
 *
 * <p>任何异常均吞掉并记 warn 日志，同时 set 空 List 以保证流程不阻断。</p>
 *
 * <p>Spring bean 名：{@code multiInstanceApproverResolver}，
 * BPMN 配置示例：
 * {@code flowable:executionListener event="start" delegateExpression="${multiInstanceApproverResolver}"}</p>
 */
@Slf4j
@Component("multiInstanceApproverResolver")
@RequiredArgsConstructor
public class MultiInstanceApproverResolver implements ExecutionListener {

    /** 流程变量名：审批人 empId 集合，MI userTask 以此作为 collection */
    public static final String VAR_APPROVER_EMP_IDS = "approverEmpIds";

    private final CandidateResolverService candidateResolverService;
    private final UserApi userApi;
    private final RepositoryService repositoryService;

    /**
     * 执行监听回调：展开审批人规则并写入流程变量。
     *
     * <p>入参/出参日志：
     * <ul>
     *   <li>入：processDefinitionId, nodeKey</li>
     *   <li>出：approverEmpIds 大小</li>
     * </ul>
     * traceId 由 common-trace 的 MDC 自动携带。</p>
     *
     * @param execution Flowable 委托执行对象
     */
    @Override
    public void notify(DelegateExecution execution) {
        String processDefinitionId = execution.getProcessDefinitionId();
        String nodeKey = execution.getCurrentActivityId();
        log.debug("[MultiInstanceApproverResolver] processDefinitionId={}, nodeKey={}", processDefinitionId, nodeKey);

        try {
            // 通过 RepositoryService 反查真实 BPMN KEY（Flowable 7 的 processDefinitionId 为 UUID，不能 split）
            ProcessDefinition pd = repositoryService.getProcessDefinition(processDefinitionId);
            String processDefinitionKey = pd != null ? pd.getKey() : processDefinitionId;

            // 解析带前缀候选列表
            List<String> candidates = candidateResolverService.resolveCandidates(processDefinitionKey, nodeKey);

            // 展开成员工 ID 集合（去重，保序）
            Set<String> empIds = expandCandidatesToEmpIds(candidates);

            List<String> empIdList = new ArrayList<>(empIds);
            execution.setVariable(VAR_APPROVER_EMP_IDS, empIdList);
            log.info("[MultiInstanceApproverResolver] 节点 {} 展开审批人 {} 个: {}",
                    nodeKey, empIdList.size(), empIdList);

        } catch (Exception e) {
            // 吞异常：不阻断流程，set 空 List 保证 MI collection 可迭代
            log.warn("[MultiInstanceApproverResolver] 展开审批人失败，节点 {}，原因: {}", nodeKey, e.getMessage());
            execution.setVariable(VAR_APPROVER_EMP_IDS, new ArrayList<>());
        }
    }

    /**
     * 把带前缀的候选列表展开成员工 ID 集合（去重）。
     * <p>逻辑与 {@link TaskAssignmentListener#expandCandidatesToEmpIds} 保持一致。</p>
     *
     * @param candidates 带前缀候选列表（如 "ROLE:BRANCH_HEAD"、"USER:E001"）
     * @return 员工 ID 集合，去重且保序
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
                case "ORG" -> log.warn("[MultiInstanceApproverResolver] ORG 类型候选暂不支持展开，跳过 candidate={}", candidate);
                default -> {
                    // 无前缀 fallback 当 empId（与 TaskAssignmentListener 保持一致）
                    empIds.add(value);
                }
            }
        }
        return empIds;
    }
}
