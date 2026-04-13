package com.bank.branch.platform.portal.adapter;

import com.bank.branch.platform.portal.adapter.dto.PortalTodoItem;

import java.util.List;

/**
 * 工作流待办查询接口 —— 供工作台聚合待办列表与待办计数。
 *
 * <p>当前为纯接口定义，<strong>无 Spring Bean 实现</strong>；
 * 消费方应使用 {@code @Autowired(required = false)} 注入，
 * 在 Bean 为 null 时降级处理。</p>
 *
 * @deprecated V1 临时占位接口，workflow-center 仅暴露 WorkflowApi（流程启动），无 query 方法。待 WorkflowQueryFacade 创建后迁移
 */
@Deprecated
public interface WorkflowQueryApi {

    /**
     * 统计待办任务数
     *
     * @param empId 员工工号
     * @return 该员工的待办任务总数
     */
    int countPendingTasks(String empId);

    /**
     * 查询最近待办列表
     *
     * @param empId 员工工号
     * @param limit 最大返回条数
     * @return 待办列表，按发起时间倒序排列
     */
    List<PortalTodoItem> listRecentPendingTasks(String empId, int limit);
}
