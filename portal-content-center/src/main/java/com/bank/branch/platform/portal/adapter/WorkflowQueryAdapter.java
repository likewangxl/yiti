package com.bank.branch.platform.portal.adapter;

import com.bank.branch.platform.portal.adapter.dto.PortalTodoItem;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * WorkflowQueryApi 适配器，封装跨模块降级逻辑。
 * <p>V1 阶段 WorkflowQueryApi bean 不存在，自动返回空数据。</p>
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
            log.debug("WorkflowQueryApi bean is null, returning empty list (V1 fallback)");
            return Collections.emptyList();
        }
        try {
            return workflowQueryApi.listRecentPendingTasks(empId, limit);
        } catch (Exception ex) {
            log.warn("WorkflowQueryApi.listRecentPendingTasks failed for empId={}", empId, ex);
            return Collections.emptyList();
        }
    }
}
