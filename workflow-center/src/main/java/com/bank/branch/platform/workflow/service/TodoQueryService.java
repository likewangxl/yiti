package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.ApprovalLogDTO;
import com.bank.branch.platform.workflow.api.dto.TaskDetailRespDTO;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.entity.WfNodeFormConf;
import com.bank.branch.platform.workflow.enums.SlaStatus;
import com.bank.branch.platform.workflow.enums.WfErrorCode;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import com.bank.branch.platform.workflow.mapper.NodeFormConfMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

    private final TaskService taskService;
    private final HistoryService historyService;
    private final BizProcessMapMapper bizProcessMapMapper;
    private final SlaCalculationService slaCalculationService;
    private final NodeFormConfMapper nodeFormConfMapper;

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
        // 1. 构建 Flowable TaskQuery
        TaskQuery query = taskService.createTaskQuery()
                .taskCandidateOrAssigned(empId)
                .orderByTaskCreateTime()
                .desc();

        // 2. 分页查询
        long total = query.count();
        List<Task> tasks = query.listPage((pageNo - 1) * pageSize, pageSize);

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

        // 3. 加载表单配置
        String processDefinitionKey = extractProcessDefinitionKey(task.getProcessDefinitionId());
        WfNodeFormConf formConf = nodeFormConfMapper.selectByProcessDefKeyAndNodeKey(
                processDefinitionKey, task.getTaskDefinitionKey());

        // 4. 加载审批日志
        List<Comment> comments = taskService.getProcessInstanceComments(task.getProcessInstanceId());
        List<ApprovalLogDTO> approvalLogs = convertCommentsToLogs(comments);

        // 5. 组装详情响应
        TaskDetailRespDTO detail = new TaskDetailRespDTO();
        detail.setTaskInfo(taskInfo);
        if (formConf != null) {
            detail.setFormFields(formConf.getFormFields());
            detail.setEditableFields(formConf.getEditableFields());
            detail.setRequiredFields(formConf.getRequiredFields());
        }
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
        dto.setTitle(map.getBusinessKey());
        dto.setStartUser(map.getStartUser());
        dto.setTaskName(task.getName());
        dto.setTaskCreateTime(taskCreateTime);
        dto.setAssignee(task.getAssignee());
        dto.setCandidateGroups(map.getCandidateGroups());
        dto.setSlaStatus(slaStatus.getCode());
        dto.setClaimable(task.getAssignee() == null);

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
        dto.setTitle(map.getBusinessKey());
        dto.setStartUser(map.getStartUser());
        dto.setTaskName(hti.getName());
        dto.setTaskCreateTime(taskCreateTime);
        dto.setAssignee(hti.getAssignee());
        dto.setCandidateGroups(map.getCandidateGroups());
        dto.setSlaStatus(slaStatus.getCode());
        dto.setClaimable(false); // 已办任务不可签收

        return dto;
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
            logDTO.setUserId(comment.getUserId());
            logDTO.setComment(comment.getFullMessage());
            logDTO.setTime(convertToLocalDateTime(comment.getTime()));
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
