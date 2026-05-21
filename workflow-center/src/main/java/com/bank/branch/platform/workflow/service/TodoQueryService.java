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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        Map<String, Task> visibleTasks = new LinkedHashMap<>();
        for (Task task : assignedTasks) {
            visibleTasks.put(task.getId(), task);
        }
        for (Task task : candidateTasks) {
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

        // 4. 构建运行时权限（Task 2.6 增强）
        RuntimeAccessDTO runtimeAccess = new RuntimeAccessDTO();
        boolean isAssignee = empId.equals(task.getAssignee());
        runtimeAccess.setIsAssignee(isAssignee);
        runtimeAccess.setCanClaim(!isAssignee && task.getAssignee() == null); // TODO: 需检查候选人
        runtimeAccess.setCanApprove(isAssignee);
        runtimeAccess.setCanReject(isAssignee);
        runtimeAccess.setCanTransfer(isAssignee);
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

        return detail;
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
            OrgDTO mainOrg = orgApi.getUserMainOrg(startUser);
            if (mainOrg != null) {
                dto.setStartOrgId(mainOrg.getOrgCode());
                dto.setStartOrgName(mainOrg.getOrgName());
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
}
