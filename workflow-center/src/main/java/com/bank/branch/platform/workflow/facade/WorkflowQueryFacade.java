package com.bank.branch.platform.workflow.facade;

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
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * WorkflowQueryApi 的 Facade 实现。
 * <p>
 * 对外统一暴露 workflow-center 内部既有的只读查询能力，
 * 避免其他模块直接依赖内部 service。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class WorkflowQueryFacade implements WorkflowQueryApi {

    private final TodoQueryService todoQueryService;
    private final ProcessQueryService processQueryService;
    private final ProcessStartService processStartService;

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
}
