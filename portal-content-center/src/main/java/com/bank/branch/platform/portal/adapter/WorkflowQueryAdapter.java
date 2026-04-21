package com.bank.branch.platform.portal.adapter;

import com.bank.branch.platform.portal.adapter.dto.PortalTodoItem;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * WorkflowQueryApi 适配器，封装跨模块降级逻辑。
 * <p>优先调用 workflow-center 正式只读 Bean；当上游 Bean 缺失或异常时，仍返回降级空数据。</p>
 */
@Slf4j
@Service
public class WorkflowQueryAdapter {

    @Autowired(required = false)
    @Setter // for testing
    private WorkflowQueryApi workflowQueryApi;

    /**
     * 查询员工待办任务数
     *
     * @param empId 员工编号
     * @return 待办数量，WorkflowQueryApi 不可用时返回 0
     */
    public int countPending(String empId) {
        if (workflowQueryApi == null) {
            log.debug("WorkflowQueryApi bean is null, returning 0 (V1 fallback)");
            return 0;
        }
        try {
            return workflowQueryApi.countPendingTasks(empId);
        } catch (Exception ex) {
            log.warn("WorkflowQueryApi.countPendingTasks failed for empId={}", empId, ex);
            return 0;
        }
    }

    /**
     * 查询员工最近待办列表
     *
     * @param empId 员工编号
     * @param limit 最大条数
     * @return 待办列表，WorkflowQueryApi 不可用时返回空列表
     */
    public List<PortalTodoItem> listPending(String empId, int limit) {
        if (workflowQueryApi == null) {
            log.debug("WorkflowQueryApi bean is null, returning empty list fallback");
            return Collections.emptyList();
        }
        try {
            return workflowQueryApi.listRecentPendingTasks(empId, limit).stream()
                    .map(this::toPortalTodoItem)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception ex) {
            log.warn("WorkflowQueryApi.listRecentPendingTasks failed for empId={}", empId, ex);
            return Collections.emptyList();
        }
    }

    private PortalTodoItem toPortalTodoItem(TaskRespDTO taskRespDTO) {
        if (taskRespDTO == null) {
            return null;
        }
        PortalTodoItem item = new PortalTodoItem();
        item.setTaskId(taskRespDTO.getTaskId());
        item.setProcessInstanceId(taskRespDTO.getProcessInstanceId());
        item.setProcessName(defaultIfBlank(taskRespDTO.getTitle(), taskRespDTO.getBizType()));
        item.setTaskTitle(defaultIfBlank(taskRespDTO.getTaskName(), taskRespDTO.getTitle()));
        item.setInitiatorName(defaultIfBlank(taskRespDTO.getStartUserName(), taskRespDTO.getStartUser()));
        item.setInitiatedTime(formatTime(firstNonNull(taskRespDTO.getStartTime(), taskRespDTO.getTaskCreateTime())));
        item.setLightStatus(taskRespDTO.getSlaStatus());
        item.setOverdueInfo(null);
        item.setBizDetailUrl(null);
        return item;
    }

    private LocalDateTime firstNonNull(LocalDateTime preferred, LocalDateTime fallback) {
        return preferred != null ? preferred : fallback;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.toString();
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
