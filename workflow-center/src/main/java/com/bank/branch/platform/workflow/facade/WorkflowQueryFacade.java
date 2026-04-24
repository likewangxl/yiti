package com.bank.branch.platform.workflow.facade;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.ApprovalLogDTO;
import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramDTO;
import com.bank.branch.platform.workflow.api.dto.TaskDetailRespDTO;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import com.bank.branch.platform.workflow.service.ProcessQueryService;
import com.bank.branch.platform.workflow.service.ProcessStartService;
import com.bank.branch.platform.workflow.service.TodoQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * WorkflowQueryApi 的 Facade 实现。
 * <p>
 * 对外统一暴露 workflow-center 内部既有的只读查询能力，
 * 避免其他模块直接依赖内部 service。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowQueryFacade implements WorkflowQueryApi {

    /** queryParticipatedBusinessKeys 的 limit 兜底与上限值. */
    private static final int DEFAULT_PARTICIPATED_LIMIT = 10000;

    private final TodoQueryService todoQueryService;
    private final ProcessQueryService processQueryService;
    private final ProcessStartService processStartService;
    private final HistoryService historyService;
    private final TaskService taskService;
    private final CurrentUserApi currentUserApi;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<TaskRespDTO> queryTodoList(String empId, String bizType, String keyword, int pageNo, int pageSize) {
        return todoQueryService.queryTodoList(empId, bizType, keyword, pageNo, pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<TaskRespDTO> queryDoneList(String empId, String bizType, String keyword, int pageNo, int pageSize) {
        return todoQueryService.queryDoneList(empId, bizType, keyword, pageNo, pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countPendingTasks(String empId) {
        return Math.toIntExact(todoQueryService.queryTodoList(empId, null, null, 1, 1).getTotal());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TaskRespDTO> listRecentPendingTasks(String empId, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        return todoQueryService.queryTodoList(empId, null, null, 1, limit).getRecords();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskDetailRespDTO getTaskDetail(String taskId, String empId) {
        return todoQueryService.getTaskDetail(taskId, empId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ApprovalLogDTO> getProcessHistory(String processInstanceId) {
        return processQueryService.getProcessHistory(processInstanceId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessDiagramDTO getProcessNodes(String processInstanceId) {
        return processQueryService.getProcessNodes(processInstanceId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BizProcessMapDTO getProcessByBusinessKey(String businessKey) {
        return processStartService.getProcessByBusinessKey(businessKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BizProcessMapDTO getProcessByBizTypeAndBizId(String bizType, String bizId) {
        return processStartService.getProcessByBizTypeAndBizId(bizType, bizId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现细节（V1.4 S1.1 模式 B 简化版）：
     * <ol>
     *   <li>empId 为空 → 直接返回空集</li>
     *   <li>主路径 {@code historyService.createHistoricProcessInstanceQuery().involvedUser(empId)}
     *       + 可选 {@code startedAfter}，Java 侧按 processDefinitionKey 前缀过滤</li>
     *   <li>若 empId == {@code currentUserApi.getCurrentEmpId()}，用
     *       {@code taskService.createTaskQuery().taskCandidateGroupIn(currentUserApi.getCurrentCandidateGroupKeys())
     *       .taskUnassigned()} 查当前候选任务，逐个反查 processInstance 补充 businessKey</li>
     *   <li>LinkedHashSet 保持插入顺序 + 去重</li>
     *   <li>businessKey 为 null/空的记录被过滤</li>
     * </ol>
     *
     * <p>前缀过滤放在 Java 侧的原因：Flowable 7 {@code HistoricProcessInstanceQuery}
     * 仅提供 {@code processDefinitionKey(exact)} / {@code processDefinitionKeyIn}，
     * 没有 keyLike。且 listPage 返回后 stream.filter 性能可接受
     * （limit 上限 10000，集合已预过滤）。
     */
    @Override
    public Set<String> queryParticipatedBusinessKeys(String empId,
                                                     String processDefinitionKeyPrefix,
                                                     Integer timeWindowDays,
                                                     Integer limit) {
        if (isBlank(empId)) {
            return Collections.emptySet();
        }
        int effectiveLimit = resolveLimit(limit);

        Set<String> keys = new LinkedHashSet<>();

        // ---- 主路径：involvedUser 历史查询 ----
        HistoricProcessInstanceQuery histQ = historyService.createHistoricProcessInstanceQuery()
                .involvedUser(empId);
        if (timeWindowDays != null && timeWindowDays > 0) {
            Date after = Date.from(Instant.now().minus(Duration.ofDays(timeWindowDays)));
            histQ = histQ.startedAfter(after);
        }

        List<HistoricProcessInstance> historicInstances = histQ.listPage(0, effectiveLimit);
        for (HistoricProcessInstance h : historicInstances) {
            if (keys.size() >= effectiveLimit) {
                break;
            }
            if (!matchesPrefix(h.getProcessDefinitionKey(), processDefinitionKeyPrefix)) {
                continue;
            }
            String bk = h.getBusinessKey();
            if (!isBlank(bk)) {
                keys.add(bk);
            }
        }

        // ---- 辅助路径：当前用户候选组未领取任务 ----
        // 仅当 empId 等于当前登录用户时生效：避免 workflow-center 反查其他用户候选组
        // （CurrentUserApi.getCurrentCandidateGroupKeys 只返回 ThreadLocal 中的当前用户组）
        String currentEmpId = safeCurrentEmpId();
        if (empId.equals(currentEmpId) && keys.size() < effectiveLimit) {
            Set<String> groupKeys = safeCurrentCandidateGroups();
            if (!groupKeys.isEmpty()) {
                List<Task> pendingTasks = taskService.createTaskQuery()
                        .taskCandidateGroupIn(groupKeys)
                        .taskUnassigned()
                        .list();
                for (Task t : pendingTasks) {
                    if (keys.size() >= effectiveLimit) {
                        break;
                    }
                    HistoricProcessInstance pi = historyService.createHistoricProcessInstanceQuery()
                            .processInstanceId(t.getProcessInstanceId())
                            .singleResult();
                    if (pi == null) {
                        continue;
                    }
                    if (!matchesPrefix(pi.getProcessDefinitionKey(), processDefinitionKeyPrefix)) {
                        continue;
                    }
                    String bk = pi.getBusinessKey();
                    if (!isBlank(bk)) {
                        keys.add(bk);
                    }
                }
            }
        }

        return keys;
    }

    /** limit null/≤0 → 10000；>10000 → 10000；其他原值返回. */
    private static int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PARTICIPATED_LIMIT;
        }
        return Math.min(limit, DEFAULT_PARTICIPATED_LIMIT);
    }

    /** processDefinitionKey 前缀匹配（prefix 空/null 视为匹配任何）. */
    private static boolean matchesPrefix(String processDefinitionKey, String prefix) {
        if (isBlank(prefix)) {
            return true;
        }
        return processDefinitionKey != null && processDefinitionKey.startsWith(prefix);
    }

    /** 安全获取当前登录用户 empId（异常/空返回 null）. */
    private String safeCurrentEmpId() {
        try {
            return currentUserApi.getCurrentEmpId();
        } catch (Exception e) {
            log.debug("[WorkflowQueryFacade.queryParticipatedBusinessKeys] 获取当前用户失败: {}", e.toString());
            return null;
        }
    }

    /** 安全获取当前候选组标识（异常/空返回空集）. */
    private Set<String> safeCurrentCandidateGroups() {
        try {
            Set<String> groups = currentUserApi.getCurrentCandidateGroupKeys();
            return groups == null ? Collections.emptySet() : groups;
        } catch (Exception e) {
            log.debug("[WorkflowQueryFacade.queryParticipatedBusinessKeys] 获取当前候选组失败: {}", e.toString());
            return Collections.emptySet();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty() || s.trim().isEmpty();
    }
}
