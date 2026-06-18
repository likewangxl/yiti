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
import java.util.Collection;
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

        // 会签完成条件 ${rejected == true || nrOfCompletedInstances >= nrOfInstances}（FlowBpmnGenerator 生成）
        // 引用 rejected 变量。审批通过(approved=true)从不设置 rejected，首个实例完成时 JUEL 解析不到该
        // 标识符会抛 PropertyNotFoundException: Cannot resolve identifier 'rejected'。在会签入口初始化为
        // false（不覆盖已存在值），使完成条件可解析。运行期生效，无需重新发布已部署流程。
        if (execution.getVariable("rejected") == null) {
            execution.setVariable("rejected", false);
        }

        try {
            // 通过 RepositoryService 反查真实 BPMN KEY（Flowable 7 的 processDefinitionId 为 UUID，不能 split）
            ProcessDefinition pd = repositoryService.getProcessDefinition(processDefinitionId);
            String processDefinitionKey = pd != null ? pd.getKey() : processDefinitionId;

            // 解析带前缀候选列表
            List<String> candidates = candidateResolverService.resolveCandidates(processDefinitionKey, nodeKey);

            // 展开成员工 ID 集合（去重，保序）
            Set<String> empIds = expandCandidatesToEmpIds(candidates, execution);

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
    private Set<String> expandCandidatesToEmpIds(List<String> candidates, DelegateExecution execution) {
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
                // VAR：从提交方传入的流程变量取审批人 empId（会签原业绩所属人 originalOwnerEmpIds 等）
                case "VAR" -> empIds.addAll(readVarEmpIds(execution.getVariable(value)));
                case "ORG" -> log.warn("[MultiInstanceApproverResolver] ORG 类型候选暂不支持展开，跳过 candidate={}", candidate);
                default -> {
                    // 无前缀 fallback 当 empId（与 TaskAssignmentListener 保持一致）
                    empIds.add(value);
                }
            }
        }
        return empIds;
    }

    /**
     * 把流程变量值解析为 empId 集合：列表/集合逐个取，单值直接取，空/null 返回空集。
     *
     * @param varValue 流程变量值（String 或 Collection）
     * @return empId 集合（去重保序）
     */
    static Set<String> readVarEmpIds(Object varValue) {
        Set<String> ids = new LinkedHashSet<>();
        if (varValue == null) return ids;
        if (varValue instanceof Collection<?> col) {
            for (Object o : col) {
                if (o != null && !o.toString().isEmpty()) ids.add(o.toString());
            }
        } else {
            String s = varValue.toString();
            if (!s.isEmpty()) ids.add(s);
        }
        return ids;
    }
}
