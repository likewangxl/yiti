package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.ApprovalLogDTO;
import com.bank.branch.platform.workflow.api.dto.NodeFormConfDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessNodeDTO;
import com.bank.branch.platform.workflow.api.dto.RuntimeAccessDTO;
import com.bank.branch.platform.workflow.api.dto.TaskDetailRespDTO;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.entity.WfNodeFormConf;
import com.bank.branch.platform.workflow.enums.SlaStatus;
import com.bank.branch.platform.workflow.enums.WfErrorCode;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import com.bank.branch.platform.workflow.mapper.NodeFormConfMapper;
import com.bank.branch.platform.workflow.mapper.WfTaskTransferMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.task.Comment;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 待办/已办查询服务。
 * <p>
 * 提供待办列表、已办列表和任务详情查询功能。
 * 聚合 Flowable 任务数据、业务流程映射、SLA 状态和表单配置。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoQueryService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final String DISABLED_HISTORICAL_EDGE_NAME = "历史直达边已停用";

    private final TaskService taskService;
    private final HistoryService historyService;
    private final RuntimeService runtimeService;
    private final BizProcessMapMapper bizProcessMapMapper;
    private final SlaCalculationService slaCalculationService;
    private final NodeFormConfMapper nodeFormConfMapper;
    private final ObjectMapper objectMapper;
    private final CurrentUserApi currentUserApi;
    private final UserApi userApi;
    private final OrgApi orgApi;
    private final com.bank.branch.platform.workflow.service.flow.FlowDefService flowDefService;
    private final com.bank.branch.platform.workflow.mapper.WfFlowDefMapper flowDefMapper;
    /** 转交锁判定：任务存在 PENDING_ACCEPT 转交记录时，RuntimeAccess 对原办理人只读（Task 9）。 */
    private final WfTaskTransferMapper wfTaskTransferMapper;

    /**
     * 解析 JSON 字符串为 List。
     *
     * @param json JSON 字符串
     * @param typeRef 类型引用
     * @return 解析后的列表，解析失败时返回空列表
     */
    private <T> List<T> parseJsonToList(String json, TypeReference<List<T>> typeRef) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("JSON 解析失败: {}", json, e);
            return new ArrayList<>();
        }
    }

    /**
     * 查询待办列表（分页）。
     * <p>
     * 使用 Flowable TaskQuery 按候选人或办理人查询当前用户的待办任务，
     * 并关联业务映射和 SLA 状态信息。支持按业务类型过滤。
     * </p>
     *
     * @param empId    当前用户工号
     * @param bizType  业务类型过滤（可选）
     * @param keyword  标题关键字搜索（可选）
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @return 分页的待办任务列表
     */
    public PageResult<TaskRespDTO> queryTodoList(String empId, String bizType, String keyword,
                                                  int pageNo, int pageSize) {
        Set<String> candidateGroupKeys = currentUserApi.getCurrentCandidateGroupKeys();
        List<Task> tasks;
        long total;

        if (candidateGroupKeys == null || candidateGroupKeys.isEmpty()) {
            TaskQuery query = taskService.createTaskQuery()
                    .taskCandidateOrAssigned(empId)
                    .orderByTaskCreateTime()
                    .desc();
            total = query.count();
            tasks = query.listPage((pageNo - 1) * pageSize, pageSize);
        } else {
            List<Task> visibleTasks = mergeVisibleTasks(empId, candidateGroupKeys);
            total = visibleTasks.size();
            int fromIndex = Math.min((pageNo - 1) * pageSize, visibleTasks.size());
            int toIndex = Math.min(fromIndex + pageSize, visibleTasks.size());
            tasks = visibleTasks.subList(fromIndex, toIndex);
        }

        // 3. 转换为 DTO 并关联业务信息
        List<TaskRespDTO> dtos = new ArrayList<>();
        for (Task task : tasks) {
            TaskRespDTO dto = convertTaskToDTO(task);
            if (dto != null) {
                dtos.add(dto);
            }
        }

        // 4. 按 bizType 过滤（后置过滤）
        if (bizType != null && !bizType.isEmpty()) {
            dtos = dtos.stream()
                    .filter(dto -> bizType.equals(dto.getBizType()))
                    .collect(Collectors.toList());
        }

        return PageResult.of(pageNo, pageSize, total, dtos);
    }

    private List<Task> mergeVisibleTasks(String empId, Set<String> candidateGroupKeys) {
        List<Task> assignedTasks = taskService.createTaskQuery()
                .taskAssignee(empId)
                .orderByTaskCreateTime()
                .desc()
                .list();
        List<Task> candidateTasks = taskService.createTaskQuery()
                .taskCandidateGroupIn(candidateGroupKeys)
                .taskUnassigned()
                .orderByTaskCreateTime()
                .desc()
                .list();

        // 候选用户型任务：branch_approve 节点按发起人机构过滤后用 addCandidateUser 直接指派候选用户
        // （identity link 为 candidate USER 而非 candidate GROUP），不会被上面的 taskCandidateGroupIn
        // 命中。必须单独按 taskCandidateUser 查询并入，否则机构负责人等候选用户看不到该待办。
        List<Task> candidateUserTasks = taskService.createTaskQuery()
                .taskCandidateUser(empId)
                .taskUnassigned()
                .orderByTaskCreateTime()
                .desc()
                .list();

        Map<String, Task> visibleTasks = new LinkedHashMap<>();
        for (Task task : assignedTasks) {
            visibleTasks.put(task.getId(), task);
        }
        for (Task task : candidateTasks) {
            visibleTasks.putIfAbsent(task.getId(), task);
        }
        for (Task task : candidateUserTasks) {
            visibleTasks.putIfAbsent(task.getId(), task);
        }

        List<Task> mergedTasks = new ArrayList<>(visibleTasks.values());
        mergedTasks.sort(Comparator.comparing(
                Task::getCreateTime,
                Comparator.nullsLast(Date::compareTo))
                .reversed());
        return mergedTasks;
    }

    /**
     * 查询已办列表（分页）。
     * <p>
     * 使用 Flowable HistoryService 查询当前用户已完成的历史任务，
     * 并关联业务映射和 SLA 状态信息。支持按业务类型过滤。
     * </p>
     *
     * @param empId    当前用户工号
     * @param bizType  业务类型过滤（可选）
     * @param keyword  标题关键字搜索（可选）
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @return 分页的已办任务列表
     */
    public PageResult<TaskRespDTO> queryDoneList(String empId, String bizType, String keyword,
                                                  int pageNo, int pageSize) {
        // 1. 构建 HistoricTaskInstanceQuery
        HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(empId)
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .desc();

        // 2. 分页查询
        long total = query.count();
        List<HistoricTaskInstance> tasks = query.listPage((pageNo - 1) * pageSize, pageSize);

        // 3. 转换为 DTO 并关联业务信息
        List<TaskRespDTO> dtos = new ArrayList<>();
        for (HistoricTaskInstance hti : tasks) {
            TaskRespDTO dto = convertHistoricTaskToDTO(hti);
            if (dto != null) {
                dtos.add(dto);
            }
        }

        // 4. 按 bizType 过滤（后置过滤）
        if (bizType != null && !bizType.isEmpty()) {
            dtos = dtos.stream()
                    .filter(dto -> bizType.equals(dto.getBizType()))
                    .collect(Collectors.toList());
        }

        return PageResult.of(pageNo, pageSize, total, dtos);
    }

    /**
     * 获取任务详情。
     * <p>
     * 加载任务完整上下文：基础信息、业务映射、SLA 状态、表单配置和审批日志。
     * 任务不存在时抛出 WF-40403 异常。
     * </p>
     *
     * @param taskId 任务ID
     * @param empId  当前用户工号
     * @return 任务详情
     */
    public TaskDetailRespDTO getTaskDetail(String taskId, String empId) {
        // 1. 查询任务
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        if (task == null) {
            log.warn("任务不存在: taskId={}", taskId);
            throw new BizException(
                    WfErrorCode.TASK_NOT_FOUND.getCode(),
                    WfErrorCode.TASK_NOT_FOUND.getMessage());
        }

        // 2. 构建基础任务信息
        TaskRespDTO taskInfo = convertTaskToDTO(task);
        String processDefinitionKey = extractProcessDefinitionKey(task.getProcessDefinitionId());

        // 3. 构建详情对象
        TaskDetailRespDTO detail = new TaskDetailRespDTO();
        detail.setTaskInfo(taskInfo);

        // 4. 构建运行时权限（Task 2.6 增强；Task 9：转交待认领期间对原办理人只读）
        RuntimeAccessDTO runtimeAccess = new RuntimeAccessDTO();
        boolean isAssignee = empId.equals(task.getAssignee());
        boolean locked = wfTaskTransferMapper.selectActiveByTaskId(task.getId()) != null;
        runtimeAccess.setIsAssignee(isAssignee);
        runtimeAccess.setCanClaim(!isAssignee && task.getAssignee() == null && !locked); // TODO: 需检查候选人
        runtimeAccess.setCanApprove(isAssignee && !locked);
        runtimeAccess.setCanReject(isAssignee && !locked);
        runtimeAccess.setCanTransfer(isAssignee && !locked);
        runtimeAccess.setIsCandidate(false); // TODO: 需检查候选人
        detail.setRuntimeAccess(runtimeAccess);

        // 5. 加载表单配置
        WfNodeFormConf formConf = nodeFormConfMapper.selectByProcessDefKeyAndNodeKey(
                processDefinitionKey, task.getTaskDefinitionKey());
        if (formConf != null) {
            NodeFormConfDTO nodeFormConf = new NodeFormConfDTO();
            nodeFormConf.setProcessDefinitionKey(processDefinitionKey);
            nodeFormConf.setNodeKey(task.getTaskDefinitionKey());
            nodeFormConf.setFormFields(parseJsonToList(formConf.getFormFields(),
                new com.fasterxml.jackson.core.type.TypeReference<List<com.bank.branch.platform.workflow.api.dto.FormFieldDTO>>() {}));
            nodeFormConf.setEditableFields(parseJsonToList(formConf.getEditableFields(), STRING_LIST_TYPE));
            nodeFormConf.setRequiredFields(parseJsonToList(formConf.getRequiredFields(), STRING_LIST_TYPE));
            detail.setNodeFormConf(nodeFormConf);
        }

        // 6. 构建流程进度（Task 2.6 增强）
        // TODO: 通过 HistoryService 查询历史活动节点
        detail.setProcessProgress(new ArrayList<>());

        // 7. 加载审批日志
        List<Comment> comments = taskService.getProcessInstanceComments(task.getProcessInstanceId());
        List<ApprovalLogDTO> approvalLogs = convertCommentsToLogs(comments);
        detail.setApprovalLogs(approvalLogs);

        // 8. 设计器动态流程：填充当前节点命名出边作为「下一步走向」分支选项（静态 BPMN 任务为空）
        detail.setOutgoingBranches(resolveOutgoingBranches(processDefinitionKey, task.getTaskDefinitionKey()));

        return detail;
    }

    /**
     * 设计器动态流程任务的出边分支解析：仅 procDefKey 以 DSN_ 前缀的流程才反查设计器图。
     * 任何异常/未命中均返回空列表，绝不阻断任务详情主流程。
     *
     * @param processDefinitionKey 当前任务的流程定义 KEY
     * @param nodeKey              当前任务节点 key（taskDefinitionKey）
     * @return 分支选项列表（可能为空，不为 null）
     */
    private List<com.bank.branch.platform.workflow.api.dto.BranchOptionDTO> resolveOutgoingBranches(
            String processDefinitionKey, String nodeKey) {
        if (processDefinitionKey == null || !processDefinitionKey.startsWith("DSN_")) {
            return new ArrayList<>();
        }
        try {
            com.bank.branch.platform.workflow.entity.WfFlowDef def =
                    flowDefMapper.selectByDeployedProcDefKey(processDefinitionKey);
            if (def == null) {
                return new ArrayList<>();
            }
            return computeOutgoingBranches(flowDefService.getGraph(def.getId()), nodeKey);
        } catch (Exception e) {
            log.warn("[TodoQueryService.resolveOutgoingBranches] 出边解析失败 procDefKey={}, nodeKey={}",
                    processDefinitionKey, nodeKey, e);
            return new ArrayList<>();
        }
    }

    /**
     * 从设计器流程图中提取「当前节点的下一步走向分支选项」。
     * <p>规则：取当前节点的出边——
     * <ul>
     *   <li>出边本身有 outputName（命名）→ 直接作为一个分支选项；</li>
     *   <li>出边无名但目标是 GATEWAY 节点 → <b>穿透网关</b>，把该网关的命名出边作为分支选项。
     *       （真实 alloc 设计器结构：审批节点 biz_dept_review/finance_review 经无名边连到
     *       gw1_route/gw2_route 网关，命名分支「部门负责人审批/原业绩所属人会签」等挂在网关出边上。）</li>
     * </ul>
     * routeVariables 取该命名边 condition 内 op=EQ 的 field→value（驱动排他网关路由，如 corpRouteTo）。
     * 无图/无命中返回空列表。</p>
     *
     * @param graph   设计器流程图（FlowDefService.getGraph 结果）
     * @param nodeKey 当前任务节点 key（task.taskDefinitionKey）
     * @return 分支选项列表（可能为空，不为 null）
     */
    static List<com.bank.branch.platform.workflow.api.dto.BranchOptionDTO> computeOutgoingBranches(
            com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO graph, String nodeKey) {
        List<com.bank.branch.platform.workflow.api.dto.BranchOptionDTO> result = new ArrayList<>();
        if (graph == null || graph.getEdges() == null || nodeKey == null) {
            return result;
        }
        // nodeKey → nodeType（判断出边目标是否网关）
        Map<String, String> typeByKey = new java.util.HashMap<>();
        if (graph.getNodes() != null) {
            for (com.bank.branch.platform.workflow.api.dto.flow.FlowNodeDTO n : graph.getNodes()) {
                typeByKey.put(n.getNodeKey(), n.getNodeType());
            }
        }
        for (com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO e : graph.getEdges()) {
            if (!nodeKey.equals(e.getFromNodeKey())) {
                continue;
            }
            if (isVisibleBranchEdge(e)) {
                result.add(toBranchOption(e)); // 命名边直接作为分支
            } else if (e.getCondition() == null
                    && "GATEWAY".equals(typeByKey.get(e.getToNodeKey()))) {
                // 仅无条件结构边穿透网关；带条件的无名边本身就是路由，不展示网关后的选项
                String gwKey = e.getToNodeKey();
                for (com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO ge : graph.getEdges()) {
                    if (gwKey.equals(ge.getFromNodeKey()) && isVisibleBranchEdge(ge)) {
                        result.add(toBranchOption(ge));
                    }
                }
            }
        }
        return result;
    }

    /**
     * 判断命名边是否应作为任务办理按钮展示。
     * 历史直达边为保留数据而未物理删除，但已由永不命中的条件停用，不能再暴露为可选分支。
     */
    private static boolean isVisibleBranchEdge(
            com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO edge) {
        return edge.getOutputName() != null
                && !DISABLED_HISTORICAL_EDGE_NAME.equals(edge.getOutputName());
    }

    /**
     * 把一条命名出边转为分支选项 DTO：outputName=展示标签，routeVariables=该边 EQ 条件的 field→value。
     */
    private static com.bank.branch.platform.workflow.api.dto.BranchOptionDTO toBranchOption(
            com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO e) {
        com.bank.branch.platform.workflow.api.dto.BranchOptionDTO b =
                new com.bank.branch.platform.workflow.api.dto.BranchOptionDTO();
        b.setOutputName(e.getOutputName());
        b.setToNodeKey(e.getToNodeKey());
        b.setIsDefault(Boolean.TRUE.equals(e.getIsDefault()));
        Map<String, Object> vars = new LinkedHashMap<>();
        com.bank.branch.platform.workflow.api.dto.flow.FlowConditionDTO cond = e.getCondition();
        if (cond != null && cond.getConditions() != null) {
            for (com.bank.branch.platform.workflow.api.dto.flow.FlowConditionDTO.Cond c : cond.getConditions()) {
                if ("EQ".equals(c.getOp()) && c.getField() != null) {
                    vars.put(c.getField(), c.getValue()); // 选中该分支须写入的路由变量
                }
            }
        }
        b.setRouteVariables(vars);
        return b;
    }

    // ========== 内部方法 ==========

    /**
     * 将 Flowable Task 转换为 TaskRespDTO。
     *
     * @param task Flowable 任务对象
     * @return 任务响应DTO，业务映射不存在时返回 null
     */
    private TaskRespDTO convertTaskToDTO(Task task) {
        String processDefinitionKey = extractProcessDefinitionKey(task.getProcessDefinitionId());

        // 加载业务映射
        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(task.getProcessInstanceId());
        if (map == null) {
            log.warn("业务映射不存在: processInstanceId={}", task.getProcessInstanceId());
            return null;
        }

        // 计算 SLA 状态
        LocalDateTime taskCreateTime = convertToLocalDateTime(task.getCreateTime());
        SlaStatus slaStatus = slaCalculationService.calculateSlaStatus(
                processDefinitionKey, task.getTaskDefinitionKey(), taskCreateTime);

        // 组装 DTO
        TaskRespDTO dto = new TaskRespDTO();
        dto.setTaskId(task.getId());
        dto.setProcessInstanceId(task.getProcessInstanceId());
        dto.setBusinessKey(map.getBusinessKey());
        dto.setBizType(map.getBizType());
        dto.setBizId(map.getBizId());
        dto.setTitle(map.getTitle());
        dto.setStartUser(map.getStartUser());
        dto.setStartTime(map.getStartTime());
        dto.setTaskName(task.getName());
        dto.setNodeKey(task.getTaskDefinitionKey());
        dto.setTaskCreateTime(taskCreateTime);
        dto.setAssignee(task.getAssignee());
        dto.setCandidateGroups(parseJsonToList(map.getCandidateGroups(), STRING_LIST_TYPE));
        dto.setSlaStatus(slaStatus.getCode());
        // SLA 时间通过 SLA 计算服务获取
        dto.setClaimable(task.getAssignee() == null);
        enrichStartUserOrg(dto);

        return dto;
    }

    /**
     * 将 Flowable HistoricTaskInstance 转换为 TaskRespDTO。
     *
     * @param hti 历史任务实例
     * @return 任务响应DTO，业务映射不存在时返回 null
     */
    private TaskRespDTO convertHistoricTaskToDTO(HistoricTaskInstance hti) {
        String processDefinitionKey = extractProcessDefinitionKey(hti.getProcessDefinitionId());

        // 加载业务映射
        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(hti.getProcessInstanceId());
        if (map == null) {
            log.warn("业务映射不存在: processInstanceId={}", hti.getProcessInstanceId());
            return null;
        }

        // 计算 SLA 状态
        LocalDateTime taskCreateTime = convertToLocalDateTime(hti.getCreateTime());
        SlaStatus slaStatus = slaCalculationService.calculateSlaStatus(
                processDefinitionKey, hti.getTaskDefinitionKey(), taskCreateTime);

        // 组装 DTO
        TaskRespDTO dto = new TaskRespDTO();
        dto.setTaskId(hti.getId());
        dto.setProcessInstanceId(hti.getProcessInstanceId());
        dto.setBusinessKey(map.getBusinessKey());
        dto.setBizType(map.getBizType());
        dto.setBizId(map.getBizId());
        dto.setTitle(map.getTitle());
        dto.setStartUser(map.getStartUser());
        dto.setStartTime(map.getStartTime());
        dto.setTaskName(hti.getName());
        dto.setNodeKey(hti.getTaskDefinitionKey());
        dto.setTaskCreateTime(taskCreateTime);
        dto.setAssignee(hti.getAssignee());
        dto.setCandidateGroups(parseJsonToList(map.getCandidateGroups(), STRING_LIST_TYPE));
        dto.setSlaStatus(slaStatus.getCode());
        dto.setProcessStatus(map.getProcessStatus());
        dto.setClaimable(false); // 已办任务不可签收
        enrichStartUserOrg(dto);

        // 已办任务：设置完成信息（含审批结果与审批意见，2026-05-20）
        if (hti.getEndTime() != null) {
            dto.setCompleteTime(convertToLocalDateTime(hti.getEndTime()));
            enrichApprovalResult(dto, hti.getId());
        }

        return dto;
    }

    /**
     * 已办任务回填审批结果与审批意见。
     *
     * <p>TaskOperationService.approve/reject 在完成任务前会调
     * {@code taskService.addComment(taskId, pid, "APPROVE"|"REJECT", opinion)}，
     * 这里反查 Flowable Comment 表（ACT_HI_COMMENT）取首条类型为 APPROVE/REJECT
     * 的评论，填充 {@code approvalResult} + {@code opinion} 两字段。
     *
     * <p>边界：
     * <ul>
     *   <li>无评论 / 仅 TRANSFER 类型评论 → 两字段保持 null（前端展示 "-"）</li>
     *   <li>反查异常（DB 短暂故障）→ debug 日志 + 跳过，不阻断主流程</li>
     * </ul>
     */
    private void enrichApprovalResult(TaskRespDTO dto, String taskId) {
        try {
            List<org.flowable.engine.task.Comment> taskComments = taskService.getTaskComments(taskId);
            if (taskComments == null || taskComments.isEmpty()) {
                return;
            }
            for (org.flowable.engine.task.Comment c : taskComments) {
                String type = c.getType();
                if ("APPROVE".equals(type) || "REJECT".equals(type)) {
                    dto.setApprovalResult(type);
                    dto.setOpinion(c.getFullMessage());
                    return;
                }
            }
        } catch (Exception e) {
            log.debug("[TodoQueryService.enrichApprovalResult] 反查任务评论失败 taskId={}", taskId, e);
        }
    }

    /**
     * 按 dto.startUser 反查 PT_USER.userchnname + 主机构信息，回填发起人姓名与机构。
     * 任一反查失败/未命中保持 null，不阻断主流程。
     */
    private void enrichStartUserOrg(TaskRespDTO dto) {
        String startUser = dto.getStartUser();
        if (startUser == null || startUser.isEmpty()) {
            return;
        }
        try {
            dto.setStartUserName(userApi.getUserName(startUser));
        } catch (Exception e) {
            log.debug("[TodoQueryService.enrichStartUserOrg] 反查发起人姓名失败 startUser={}", startUser, e);
        }
        try {
            // 发起人工号（PT_USER.username，展示用）：startUser 为 USER_ID 内部主键
            com.bank.branch.platform.auth.api.dto.UserDTO u = userApi.getUserByEmpId(startUser);
            if (u != null) {
                dto.setStartUserEmpNo(u.getUsername());
            }
        } catch (Exception e) {
            log.debug("[TodoQueryService.enrichStartUserOrg] 反查发起人工号失败 startUser={}", startUser, e);
        }
        try {
            OrgDTO mainOrg = orgApi.getUserMainOrg(startUser);
            if (mainOrg != null) {
                dto.setStartOrgId(mainOrg.getOrgCode());
                dto.setStartOrgName(mainOrg.getOrgName());
                dto.setStartOrgDeptNo(mainOrg.getDeptNo());
            }
        } catch (Exception e) {
            log.debug("[TodoQueryService.enrichStartUserOrg] 反查发起人主机构失败 startUser={}", startUser, e);
        }
    }

    /**
     * 将 Flowable Comment 列表转换为审批日志 DTO 列表。
     *
     * @param comments 审批评论列表
     * @return 审批日志DTO列表
     */
    private List<ApprovalLogDTO> convertCommentsToLogs(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return new ArrayList<>();
        }
        List<ApprovalLogDTO> logs = new ArrayList<>();
        for (Comment comment : comments) {
            ApprovalLogDTO logDTO = new ApprovalLogDTO();
            logDTO.setOperator(comment.getUserId());
            logDTO.setOpinion(comment.getFullMessage());
            logDTO.setOperateTime(convertToLocalDateTime(comment.getTime()));
            // nodeKey/nodeName/action/operatorName/operatorOrgName 在 Task 2.6 中通过 HistoryService 补充
            logs.add(logDTO);
        }
        return logs;
    }

    /**
     * 从 processDefinitionId（格式：key:version:deployId）中提取 processDefinitionKey。
     *
     * @param processDefinitionId Flowable 流程定义ID
     * @return 流程定义KEY
     */
    private String extractProcessDefinitionKey(String processDefinitionId) {
        if (processDefinitionId == null) {
            return null;
        }
        return processDefinitionId.split(":")[0];
    }

    /**
     * 将 java.util.Date 转换为 LocalDateTime。
     *
     * @param date 日期
     * @return LocalDateTime，date 为 null 时返回 null
     */
    private LocalDateTime convertToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    // ============== TodoQueryApi 支持方法 ==============
    // 供 perf 等业务模块按 businessKey 反查 task 元信息（业务字段过滤留在业务模块，不让 workflow 认业务表）
    // 注意：Flowable Task 不直接持 businessKey，需 join BIZ_PROCESS_MAP 表反查

    /**
     * 查询当前员工在某 bizType 下所有待办 task 的 businessKey（去重）。
     * <p>口径与 queryTodoList 对齐：候选 group 模式走 mergeVisibleTasks，否则走 taskCandidateOrAssigned；
     * 然后用 task.processInstanceId 批量 IN 查 BIZ_PROCESS_MAP 取 businessKey。</p>
     *
     * @param empId   员工 ID
     * @param bizType 业务类型（BIZ_PROCESS_MAP.biz_type，如 "ALLOC_ADJUST"）
     * @return businessKey 列表（去重，可能为空）
     */
    public List<String> listMyTodoBusinessKeys(String empId, String bizType) {
        if (empId == null || bizType == null) {
            return new ArrayList<>();
        }
        // PC 管理端会话链路：候选组取「当前登录用户」（读登录态 ThreadLocal）
        return doListTodoBusinessKeys(empId, bizType, currentUserApi.getCurrentCandidateGroupKeys());
    }

    /**
     * 同 {@link #listMyTodoBusinessKeys}，但候选组按传入 empId 查库实时解析（<b>不读登录态 ThreadLocal</b>）。
     * <p>供 SOAP 网关 / callpu 等<b>无会话上下文</b>链路调用——这些请求线程没有登录态，
     * 若走 currentUserApi 会抛 AUTH-40105「未登录或会话已过期」。
     * PC 管理端请继续用 {@link #listMyTodoBusinessKeys}（保持会话登录语义）。</p>
     *
     * @param empId   员工 ID（由上游渠道认证后透传）
     * @param bizType 业务类型
     * @return businessKey 列表（去重，可能为空）
     */
    public List<String> listTodoBusinessKeysByEmp(String empId, String bizType) {
        if (empId == null || bizType == null) {
            return new ArrayList<>();
        }
        // 无会话链路：候选组按入参 empId 查库解析
        return doListTodoBusinessKeys(empId, bizType, userApi.getCandidateGroupKeys(empId));
    }

    /**
     * 待办 businessKey 查询的公共实现；候选组由调用方按链路（会话 / 无会话）传入，本方法不感知来源。
     */
    private List<String> doListTodoBusinessKeys(String empId, String bizType, Set<String> candidateGroupKeys) {
        List<Task> tasks;
        if (candidateGroupKeys == null || candidateGroupKeys.isEmpty()) {
            tasks = taskService.createTaskQuery()
                    .taskCandidateOrAssigned(empId)
                    .list();
        } else {
            tasks = mergeVisibleTasks(empId, candidateGroupKeys);
        }
        if (tasks.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> piids = tasks.stream()
                .map(Task::getProcessInstanceId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (piids.isEmpty()) {
            return new ArrayList<>();
        }
        List<BizProcessMap> maps = bizProcessMapMapper.selectByProcessInstanceIdsAndBizType(piids, bizType);
        return maps.stream()
                .map(BizProcessMap::getBusinessKey)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 按 businessKey 列表批量反查 TaskRespDTO（候选 OR 受理口径，复用 convertTaskToDTO 装配）。
     * <p>实现：businessKey 单条 selectByBusinessKey（N+1 但 N≤pageSize≤100 可控）→ 拿 processInstanceId →
     * 与当前用户全部待办 task 内存交集 → convertTaskToDTO 装配。</p>
     *
     * @param empId        员工 ID（鉴权用，只返该员工候选或受理的 task）
     * @param businessKeys 待查的 businessKey 列表
     * @return TaskRespDTO 列表（不命中的 key 不返回）
     */
    public List<TaskRespDTO> findMyTaskRespByBusinessKeys(String empId, List<String> businessKeys) {
        if (empId == null || businessKeys == null || businessKeys.isEmpty()) {
            return new ArrayList<>();
        }
        // PC 管理端会话链路：候选组取「当前登录用户」（读登录态 ThreadLocal）
        return doFindTaskRespByBusinessKeys(empId, businessKeys, currentUserApi.getCurrentCandidateGroupKeys());
    }

    /**
     * 同 {@link #findMyTaskRespByBusinessKeys}，但候选组按传入 empId 查库实时解析（<b>不读登录态</b>）。
     * 供 SOAP 网关 / callpu 等无会话上下文链路调用，避免 AUTH-40105；PC 管理端请继续用
     * {@link #findMyTaskRespByBusinessKeys}（保持会话登录语义）。
     */
    public List<TaskRespDTO> findTaskRespByBusinessKeysByEmp(String empId, List<String> businessKeys) {
        if (empId == null || businessKeys == null || businessKeys.isEmpty()) {
            return new ArrayList<>();
        }
        return doFindTaskRespByBusinessKeys(empId, businessKeys, userApi.getCandidateGroupKeys(empId));
    }

    /**
     * 按 businessKey 反查 TaskRespDTO 的公共实现；候选组由调用方按链路传入，本方法不感知来源。
     */
    private List<TaskRespDTO> doFindTaskRespByBusinessKeys(String empId, List<String> businessKeys,
                                                           Set<String> candidateGroupKeys) {
        // 1. businessKey → processInstanceId（N+1 在 pageSize≤100 可控）
        Set<String> targetPiids = new HashSet<>();
        for (String bk : businessKeys) {
            BizProcessMap map = bizProcessMapMapper.selectByBusinessKey(bk);
            if (map != null && map.getProcessInstanceId() != null) {
                targetPiids.add(map.getProcessInstanceId());
            }
        }
        if (targetPiids.isEmpty()) {
            return new ArrayList<>();
        }
        // 2. 拿该员工全部待办 task，再按 processInstanceId 内存交集（保鉴权口径）
        List<Task> allTasks;
        if (candidateGroupKeys == null || candidateGroupKeys.isEmpty()) {
            allTasks = taskService.createTaskQuery()
                    .taskCandidateOrAssigned(empId)
                    .list();
        } else {
            allTasks = mergeVisibleTasks(empId, candidateGroupKeys);
        }
        return allTasks.stream()
                .filter(t -> targetPiids.contains(t.getProcessInstanceId()))
                .map(this::convertTaskToDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 已办：查询当前员工已办 (finished) task 的 businessKey（去重）。
     * <p>走 HistoryService.createHistoricTaskInstanceQuery + finished + taskAssignee=empId
     * + processInstanceId 批量 IN 查 BIZ_PROCESS_MAP 过滤 bizType。</p>
     */
    public List<String> listMyDoneBusinessKeys(String empId, String bizType) {
        if (empId == null || bizType == null) {
            return new ArrayList<>();
        }
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(empId)
                .finished()
                .list();
        if (tasks.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> piids = tasks.stream()
                .map(HistoricTaskInstance::getProcessInstanceId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (piids.isEmpty()) {
            return new ArrayList<>();
        }
        List<BizProcessMap> maps = bizProcessMapMapper.selectByProcessInstanceIdsAndBizType(piids, bizType);
        return maps.stream()
                .map(BizProcessMap::getBusinessKey)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 已办：按 businessKey 列表反查 TaskRespDTO（鉴权：仅返该员工 assignee 的 HistoricTask）。
     */
    public List<TaskRespDTO> findDoneTaskRespByBusinessKeys(String empId, List<String> businessKeys) {
        if (empId == null || businessKeys == null || businessKeys.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> targetPiids = new HashSet<>();
        for (String bk : businessKeys) {
            BizProcessMap map = bizProcessMapMapper.selectByBusinessKey(bk);
            if (map != null && map.getProcessInstanceId() != null) {
                targetPiids.add(map.getProcessInstanceId());
            }
        }
        if (targetPiids.isEmpty()) {
            return new ArrayList<>();
        }
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(empId)
                .finished()
                .list();
        return tasks.stream()
                .filter(t -> targetPiids.contains(t.getProcessInstanceId()))
                .map(this::convertHistoricTaskToDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
