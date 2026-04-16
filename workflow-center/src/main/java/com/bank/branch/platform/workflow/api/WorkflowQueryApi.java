package com.bank.branch.platform.workflow.api;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.workflow.api.dto.ApprovalLogDTO;
import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramDTO;
import com.bank.branch.platform.workflow.api.dto.TaskDetailRespDTO;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;

import java.util.List;

/**
 * 工作流只读查询 API。
 * <p>
 * 为其他业务模块提供任务查询、流程历史和流程映射等只读能力。
 * 所有实现必须复用 workflow-center 现有查询 service 和 DTO，避免平行模型。
 * </p>
 */
public interface WorkflowQueryApi {

    /**
     * 查询待办列表。
     *
     * @param empId 当前员工工号
     * @param bizType 业务类型过滤，可为空
     * @param keyword 关键字过滤，可为空
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @return 待办分页结果
     */
    PageResult<TaskRespDTO> queryTodoList(String empId, String bizType, String keyword, int pageNo, int pageSize);

    /**
     * 查询已办列表。
     *
     * @param empId 当前员工工号
     * @param bizType 业务类型过滤，可为空
     * @param keyword 关键字过滤，可为空
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @return 已办分页结果
     */
    PageResult<TaskRespDTO> queryDoneList(String empId, String bizType, String keyword, int pageNo, int pageSize);

    /**
     * 统计待办总数。
     *
     * @param empId 当前员工工号
     * @return 待办总数
     */
    int countPendingTasks(String empId);

    /**
     * 查询最近待办。
     *
     * @param empId 当前员工工号
     * @param limit 最大返回条数
     * @return 最近待办列表
     */
    List<TaskRespDTO> listRecentPendingTasks(String empId, int limit);

    /**
     * 获取任务详情。
     *
     * @param taskId 任务 ID
     * @param empId 当前员工工号
     * @return 任务详情
     */
    TaskDetailRespDTO getTaskDetail(String taskId, String empId);

    /**
     * 获取流程历史。
     *
     * @param processInstanceId 流程实例 ID
     * @return 流程历史节点列表
     */
    List<ApprovalLogDTO> getProcessHistory(String processInstanceId);

    /**
     * 获取流程进度节点。
     *
     * @param processInstanceId 流程实例 ID
     * @return 流程进度节点图
     */
    ProcessDiagramDTO getProcessNodes(String processInstanceId);

    /**
     * 根据业务键获取流程映射。
     *
     * @param businessKey 业务键
     * @return 流程映射
     */
    BizProcessMapDTO getProcessByBusinessKey(String businessKey);

    /**
     * 根据业务类型和业务 ID 获取流程映射。
     *
     * @param bizType 业务类型
     * @param bizId 业务 ID
     * @return 流程映射
     */
    BizProcessMapDTO getProcessByBizTypeAndBizId(String bizType, String bizId);
}
